import sys
import git
import os
import logging

from metadata_miner.head_metadata import HeadMetadata
from osv_preprocess.osv_range import EventType, OsvRange
"""
This metadata aims to detetct wether an unpatched head contains all the file associated to the fixed event described in a range
It tracks file renaming (one time only) 
"""


class DivergenceMetadata(HeadMetadata):
    def __init__(self,range : OsvRange,workspace:str, fork : str, head : str,log_path: str = "log"):
        super().__init__(range=range,workspace=workspace,fork=fork,head=head,log_dir=log_path)
        print(str(self.range.events.items()))
        

        
    def _are_files_in_commit(self,repo_fix_path, commit_fix,commit_target,repo_target_path):
        
        fix_files = self._list_diff_files(repo_fix_path,commit_fix)
        all_file_found = True
        
        if (self._commit_exists(repo_target_path, commit_target)):
            #First Check if all files are present in the target commit 
            for file in fix_files:
                self.logger.info(f"Checking {file} in {commit_target}")
                if(not self._detect_file_or_renamed_file_present(repo_target_path,file,commit_target)):
                    self.logger.info(f"{file} not found")
                    all_file_found=False
                    break
        else:
                self.logger.info(f"Commit {commit_target} not found in {repo_target_path}, using default head instead")
                #Then if not check if the file are present in the target of the main branch
                default_branch_head = self._get_default_branch_head(repo_target_path)
                for file in fix_files:
                    self.logger.info(f"Checking {file} in default branch HEAD")
                    if(not self._detect_file_or_renamed_file_present(repo_target_path,file,default_branch_head)):
                        self.logger.info(f"{file} not found in default branch head")
                        all_file_found=False
                        break
                if all_file_found:
                    self.logger.info(f"All files found in default branch HEAD")   
                else:
                    self.logger.info("All files not found in default branch head")

        return all_file_found
    
    def computeMetadata(self):
            self.logger.info(f"--------------Computing Divergence Metadata for fork: {self.fork_path if self.fork_path else ''}, head: {self.head}, osv_range: {self.range}--------------")
            result = 0
            on_error = 0

            
            fix_events = set([id for id,event  in self.range.events.items() if event == EventType.FIXED])
            for event_hash in fix_events :
                        try:
                            if  self._are_files_in_commit(self.upstream_path,event_hash,self.head,self.fork_path):
                                result= 1
                                break
                        except Exception as e:
                            self.logger.info(f"Error while checking event {event_hash} {EventType} in fork {self.fork_path}: {e}", exc_info=True)
                            on_error+=1
                          
            if on_error == len(fix_events):
                self.logger.error(f"All events {fix_events} failed to be checked in fork {self.fork_path}")
                return -1
            self.logger.info(f"--------------Result for fork {self.fork_path} and head {self.head}: {result}--------------")
            return result
    
    



    def _list_diff_files(self,repo_path, commit_hash):
        """
        Lists all files changed in a given commit.

        :param repo_path: Path to the Git repository.
        :param commit_hash: The commit hash to check.
        :return: A list of modified file paths.
        """
        repo = git.Repo(repo_path)
        commit = repo.commit(commit_hash)
        parent_commit = commit.parents[0] if commit.parents else None
        
        if not parent_commit:
            self.logger.info("No parent commit found. This is likely the first commit.")
            return []
        
        diff = commit.diff(parent_commit)
        modified_files = [item.a_path for item in diff if item.change_type == 'M']

        return modified_files


    
    
    def _get_default_branch_head(self,repo_path='.'):
        repo = git.Repo(repo_path)
        origin = repo.remotes.origin

      
        # Get the reference of the default branch (origin/HEAD)
        origin_head = repo.refs['origin/HEAD']

        # Resolve the actual reference (e.g., origin/main or origin/master)
        default_branch = origin_head.reference

        # Get the commit hash
        commit_hash = default_branch.commit.hexsha
        return commit_hash



    
    def _detect_file_or_renamed_file_present(self,repo_path, file_path,commit):
        if(self._is_file_in_commit(repo_path, commit, file_path)):
            self.logger.info(f"File {file_path} exists in commit {commit}")
            return True
        else: 
            self.logger.info(f"Searching for rename of {file_path} in commit {commit}")
            renamed_file = self._detect_deletion_and_rename(repo_path, file_path)
            if renamed_file and self._is_file_in_commit(repo_path, commit, renamed_file):
                self.logger.info(f"Found renamed file {renamed_file}  in commit {commit}")
                return True
            else:
                self.logger.info(f"{file_path} not found in commit and no rename detected")
                return False
            
    


    def _commit_exists(self, repo_path: str, commit_hash: str) -> bool:
        """
        Check if a given commit hash exists in the Git repository.

        :param repo_path: 
        :param commit_hash: The commit hash to check
        :return: True if the commit exists, False otherwise
        """
        try:
            repo = git.Repo(repo_path)
            repo.commit(commit_hash)
            return True
        except ( Exception):
            return False
     

    def _is_file_in_commit(self,repo_path, commit_hash, file_path):
        """
        Checks if a file exists in a specific commit.

        Args:
            repo_path (str): The path to the Git repository.
            commit_hash (str): The hash of the commit to check.
            file_path (str): The relative file path to check in the commit.

        Returns:
            bool: True if the file exists in the commit, False otherwise.
        """
        try:
            # Initialize the repository
            repo = git.Repo(repo_path)
            # Get the commit object
            commit = repo.commit(commit_hash)

            # Check if the file exists in the commit
            tree = commit.tree
            for entry in tree.traverse():
                if entry.path == file_path:
                    return True
            return False

        except Exception as e:
            self.logger.info(f"Git command error in _is_file_in_commit() : {e}")
            return False
        # except Exception as e:
        #     self.logger.error(e, exc_info=True)
        #     print(f"Errodr: {e}")
        #     return False
    def _detect_deletion_and_rename(self,repo_path, file_path):
        """
        Detects whether a file has been renamed or deleted in a Git repository.

        This function checks the Git log to detect file renames and deletions,
        and calls the `detectRename` function if a file deletion is found to see
        if the file was renamed in the same commit.
        Note : it will only detect the first rename.
        Args:
            repo_path (str): The path to the Git repository.
            file_path (str): The path to the file to check for renames and deletions.
        """
        repo = git.Repo(repo_path)

        try:
            # Step 1: Check for renames and deletions with `git log --follow --pretty=oneline`
            log_output = repo.git.log('--follow', '--find-renames=90%', '--name-status', '--pretty=oneline', '--', file_path)
            
            rename_history = []
            deletion_commit = None
            renamed_file =""
            # Loop through the log output, which contains the commit hash and message
            lines = log_output.splitlines()
            for i in range(0, len(lines), 2):
                commit_info = lines[i]
                file_status_line = lines[i+1] if i+1 < len(lines) else None
                commit_hash = commit_info.split(" ")[0]

                if file_status_line:
                    parts = file_status_line.split("\t")
                    status = parts[0]
                    file = parts[1]

                    if status == "R":  # Renamed file detected
                        renamed_file = parts[2]
                        break  # Stop after the first rename
                    elif status == "D":  # File deletion detected
                        deletion_commit = commit_hash  # The commit where the file was deleted        
            
            if deletion_commit:
                # Check for renames in the same commit as the deletion
                renamed_file = self._detectRename(repo, deletion_commit, file_path)
        
            return renamed_file
        except git.exc.GitCommandError as e:
            self.logger.info(f"Git command error in _detect_deletion_and_rename() : {e}")  
            return ""

    def _detectRename(self,repo ,deletion_commit, path):
        """
        Detects if a file was renamed in the same commit where it was deleted.

        This function checks the commit where the file was deleted for any renames.
        It uses `git diff-tree` to check for renames, and checks if the deleted file
        matches the file in the diff output. If a rename is detected, it prints the details.

        Args:
            repo (git.Repo): The Git repository object.
            deletion_commit (str): The commit hash where the file was deleted.
            path (str): The path to the deleted file to check for renames.
        """
        # Check for renames and other changes in the deletion commit
        # Use `git diff-tree` to check for renames in the commit where the file was deleted
        diff_output = repo.git.diff_tree('--no-commit-id', '--find-renames', deletion_commit, '-r')

        renamed_file=""

        for line in diff_output.splitlines():
            line = line.replace("\t", " ")
            parts = line.split(" ")
            status = parts[4]
            if status.startswith("R"):  # Renamed file detected
                old_file_path = parts[5]
                if old_file_path == path:
                    new_file_path = parts[6]
                    renamed_file =new_file_path

        return renamed_file


       