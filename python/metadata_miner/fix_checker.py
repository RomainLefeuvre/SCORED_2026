
import subprocess

from metadata_miner.TqdmToLogger import TqdmToLogger as ProgressBar
from metadata_miner.range_metadata import RangeMetadata
from osv_preprocess.osv_range import EventType, OsvRange
from tqdm import tqdm









import logging
import datetime
from metadata_miner.toolkit import *
class FixCheckerMetadata(RangeMetadata):
    def __init__(self, range: OsvRange, fork: str,log_path: str = "log"):
        super().__init__(range,fork,log_path)
       

    def computeMetadata(self):
        fixes:set[str]= get_fixed(self.range)
        
        if len(fixes) == 0:
            self.logger.info("No fixes found in the range.")
            return 1
        result=0
        for fix in fixes :
            result = self.commit_in_default_branch(fix, self.upstream_path)
            if result == 1:
                break
        return result
    def commit_in_default_branch(self,commit_hash: str, repo_path: str = ".") -> bool:
        try:
            if commit_exists(commit_hash=commit_hash, repo_path=repo_path):

                # Get the default branch (HEAD symbolic ref)
                result = subprocess.run(
                    ["git", "symbolic-ref", "refs/remotes/origin/HEAD"],
                    cwd=repo_path,
                    check=True,
                    stdout=subprocess.PIPE,
                    text=True,
                )
                default_branch = result.stdout.strip().split("/")[-1]

                # Check if the commit is reachable from the default branch
                result = subprocess.run(["git", "merge-base", "--is-ancestor", commit_hash, 
                                         f"origin/{default_branch}"],            cwd=repo_path,)

                if result.returncode == 0:
                    return 1  # Commit is in default branch
                else:
                    return 0 
            else:

                return -1

        except subprocess.CalledProcessError:
            return -1


            

