
import subprocess

from git import Repo

from metadata_miner.TqdmToLogger import TqdmToLogger as ProgressBar
from metadata_miner.range_metadata import RangeMetadata
from osv_preprocess.osv_range import EventType, OsvRange
from tqdm import tqdm



import logging
import datetime
from metadata_miner.toolkit import *
class CveIdInHistoryMetadata(RangeMetadata):
    def __init__(self, range: OsvRange, fork: str,log_path: str = "log"):
        super().__init__(range,fork,log_path)
       

    def computeMetadata(self):
        try:
            commit_result = self.find_commit_with_string(self.fork_path, self.range.vulnerability_id, in_message=True, in_diff=True)
        except Exception as e:
            self.logger.error(f"Error while searching for {self.range.vulnerability_id} in {self.fork_path}: {e}")
            return -1
        if len(commit_result) == 0:
            result = 1
        else:
            result = 0
            for commit in commit_result:
                self.logger.info(f"Found {self.range.vulnerability_id} in {self.fork_path} in  {commit}")
        return result
   

    def find_commit_with_string(self,repo_path: str, search_string: str, in_message=True, in_diff=True):
        """
        Search for a string in the commits of a git repository.

        Args:
            repo_path (str): Path to the local git repository.
            search_string (str): String to search for.
            in_message (bool): Whether to search in commit messages.
            in_diff (bool): Whether to search in commit diffs.

        Returns:
            List of commit hashes where the string is found.
        """
        repo = Repo(repo_path)
        matching_commits = []
        tqdm_logger = ProgressBar(self.logger)
        for commit in tqdm(repo.iter_commits(), file=tqdm_logger,mininterval=60):
            found = False
            if in_message and search_string in commit.message:
                found = True

            # if in_diff:
            #     diff_data = commit.diff(create_patch=True)
            #     for diff in diff_data:
            #         if diff.diff and search_string.encode() in diff.diff:
            #             found = True
            #             break

            if found:
                matching_commits.append(commit.hexsha)

        return matching_commits


