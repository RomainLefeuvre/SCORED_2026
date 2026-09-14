import json
import subprocess
import sys
import threading
import git
import os
import logging
from multiprocessing import Manager  # Add this import
from metadata_miner.head_metadata import HeadMetadata
from osv_preprocess.osv_range import EventType, OsvRange
"""

"""


class MainBranchMetadata(HeadMetadata):
    swhid2branch=None
    path="swhid_to_branch.json"
    fork_2_mainbranches = Manager().dict()
    fork_2_mainbranches = None
    def __init__(self,range : OsvRange,workspace:str,fork : str, head : str,log_path: str = "log"):
        super().__init__(range=range,workspace=workspace,fork=fork,head=head,log_dir=log_path)
        
    

        
    
    def computeMetadata(self):
        if not self._get_swhid_head() in MainBranchMetadata.swhid2branch:
           #self.logger.warn(f"Commit {self.head} not found in the swhid to branch map")
           return 0;
        else:
            try:
                self.logger.info("Retrieving default branch")
                default_branch = self._get_default_branch()
                self.logger.info(f"Default branch is {default_branch}")
                current_branches = MainBranchMetadata.swhid2branch[self._get_swhid_head()]
                current_branches= [branch.strip().split('/')[-1] for branch in current_branches]
                for branch in current_branches :
                    if default_branch ==  branch:
                        return 1
                
                self.logger.info(f"Commit {self.head} is not in the default branch, {current_branches} instead")
                return 0
            except Exception as e:
                self.logger.error(e)
                return -1
        
    def _get_swhid_head(self):
        return f"swh:1:rev:{self.head}"
    def _get_default_branch(self):
        if self.fork_path in MainBranchMetadata.fork_2_mainbranches:
            return MainBranchMetadata.fork_2_mainbranches[self.fork_path]
        # Run git symbolic-ref refs/remotes/origin/HEAD to get the default branch
        result : subprocess.CompletedProcess[str]= subprocess.run(
            ['git', 'symbolic-ref', 'refs/remotes/origin/HEAD'],
            cwd=self.fork_path,
            capture_output=True,
            text=True,
            check=True
        )

        ref = result.stdout.strip()
        default_branch = ref.split('/')[-1]

        
        MainBranchMetadata.fork_2_mainbranches[self.fork_path] = default_branch
        return default_branch
        

    @classmethod
    def initswhid_2_branch(cls):
        """
        Load the json map from the file
        """
        if cls.fork_2_mainbranches is None:
            manager = Manager()
            cls.fork_2_mainbranches = manager.dict()
        if cls.swhid2branch is None:
                   if os.path.exists(cls.path):
                        with open(cls.path, "r") as f:
                            cls.swhid2branch = json.load(f)
                   else: 
                       raise Exception(f"File {cls.path} does not exist")
                   
       
