
import subprocess
import os
print("CWD:", os.getcwd())
from metadata_miner.TqdmToLogger import TqdmToLogger as ProgressBar
from metadata_miner.range_metadata import RangeMetadata
from osv_preprocess.osv_range import EventType, OsvRange
from tqdm import tqdm



cache:dict[(str,str),str] = dict()#eventually maybe

def get_patchids(logger, commit_id: str, fork_path: str):
    try:
        p1 = subprocess.Popen(
            ["git", "show", "--diff-merges=separate", commit_id],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            cwd=fork_path,
            errors="replace"
        )
        p2 = subprocess.Popen(
            ["git", "patch-id"],
            stdin=p1.stdout,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            cwd=fork_path,
            errors="replace"
        )
        p1.stdout.close()  # Allow p1 to receive a SIGPIPE if p2 exits.

        patch_out, err2 = p2.communicate()
        _, err1 = p1.communicate()

        if p2.returncode != 0:
            logger.debug(f"patch-id failed: {err2}")
            return None, None, None
        if p1.returncode != 0:
            logger.debug(f"git show failed: {err1}")
            return None, None, None

        if not patch_out.strip():
            return None, None, None

        lines = [line.split(" ", 1) for line in patch_out.strip().splitlines()]
        patch_ids, fixes = zip(*lines) if lines else ([], [])

        # For the commit message, run git show separately or parse here
        # This avoids the extra git show call but complicates code.
        # For simplicity, you can still do a lightweight git show to get message only.
        
        result_show = subprocess.run(
            ["git", "show", "--no-patch", "--format=%B", commit_id],
            capture_output=True,
            text=True,
            check=True,
            cwd=fork_path,
            errors="replace"
        ).stdout
        return list(patch_ids), list(fixes), result_show
    except subprocess.CalledProcessError as e:
        logger.debug(e)
        return None, None, None

def get_commit_date(commit_id: str, fork_path: str) -> str:
    """Returns the patch ID of a given commit and the message"""
    try:
        result = subprocess.run(["git", "show","--no-patch",r"--format=%cs", commit_id],capture_output=True,text=True,check=True,cwd=fork_path, errors="replace")

        return datetime.datetime.strptime(result.stdout.strip(), "%Y-%m-%d")  # Extract the patch ID
    except subprocess.CalledProcessError as e:
        raise RuntimeError(f"Git command failed: {e}")



import logging
import datetime

class CherryPickMetadata(RangeMetadata):
    def __init__(self, range: OsvRange,workspace:str, fork: str,log_path: str = "log"):
        super().__init__(workspace=workspace,range=range,fork=fork,log_dir=log_path)
       

    def computeMetadata(self):
        self.logger.info(self.range.vulnerability_id+"_"+str(self.range.events))
        try:
            fixes_ids = [key for key, value in self.range.events.items() if value == EventType.FIXED]
            
            
            upstream_patchids_mapping = {
                patchid: fix
                for commit_id in fixes_ids
                for patchid, fix in zip(*get_patchids(self.logger, commit_id, self.upstream_path)[:2])
                if patchid is not None
            }
            
            upstream_patchids = set([key for key in upstream_patchids_mapping.keys() if key is not None])

            if len(upstream_patchids) == 0:
                self.logger.error("No fix in upstream")
                return -1
            
            earlier_fix_date = min([get_commit_date(value,self.upstream_path) for value in set(upstream_patchids_mapping.values())])
            
            #getting all commits of the fork
            result = subprocess.run(
                ["git", "log", "--pretty=format:%H","--after",str(earlier_fix_date),"--first-parent","HEAD"],# ?
                capture_output=True,
                text=True,
                check=True,
                cwd=self.fork_path,
            )
            fork_commits_to_explore = result.stdout.splitlines()

            
            tqdm_logger = ProgressBar(self.logger)
            for fork_commit_id in tqdm(fork_commits_to_explore, file=tqdm_logger,mininterval=120):
                current_fork_patchids,current_fork_parent, message = get_patchids(self.logger,fork_commit_id, self.fork_path)
                if message and any(current_fork_patchid in upstream_patchids for current_fork_patchid in current_fork_patchids):
                    self.logger.info(f"Patchid {str(current_fork_patchids)} identical to one of the upstram patchids")
                    if not "cherry picked from" in message:
                        self.logger.error(f"Patchid {str(current_fork_patchids)} is not well formated, missing 'cherry picked from' in {message}")
                        return 0
        except Exception as e :
            self.logger.error(e,exc_info=True)
            return -1
        return 1




            
