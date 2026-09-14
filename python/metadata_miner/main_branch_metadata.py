import json
import subprocess
import sys
import threading
import git
import os
import logging
from multiprocessing import Manager  # Add this import
from metadata_miner.fork_metadata import ForkMetadata
from metadata_miner.head_metadata import HeadMetadata
from osv_preprocess.osv_range import EventType, OsvRange
"""

"""


class MainBranchMetadata(ForkMetadata):
    def __init__(self,fork : str,workspace:str, log_path: str = "log"):
        super().__init__(fork=fork,workspace=workspace,log_dir=log_path,clone=True)
        
    def computeMetadata(self):
     
            try:
               return self._get_default_branch()
            except Exception as e:
                self.logger.error(e)
                return "-1"
        
    def _get_swhid_head(self):
        return f"swh:1:rev:{self.head}"
    def _get_default_branch(self):
       
        result : subprocess.CompletedProcess[str]= subprocess.run(
            ['git', 'symbolic-ref', 'refs/remotes/origin/HEAD'],
            cwd=self.fork_path,
            capture_output=True,
            text=True,
            check=True
        )

        ref = result.stdout.strip()
        default_branch = ref.split('/')[-1]

        
        return default_branch
        

 