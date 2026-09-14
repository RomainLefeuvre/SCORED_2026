from pathlib import Path
from typing import List, Tuple
from concurrent.futures import ThreadPoolExecutor
import subprocess
import sys
import os
import logging
from osv_preprocess.osv_range import OsvRange

class ForkMetadata:
    def __init__(self, fork:str, workspace:str, log_dir:str = "log", clone:bool = True):
        self.workspace=workspace
        self.fork_remote_url = fork
        Path(self.workspace).mkdir(parents=True, exist_ok=True)
        # Clone upstream and fork repositories
        raw = fork.replace("https://","").replace("git@", "").replace(".git", "")
        self.repo_name = raw.split("/")[-1]
        self.repo_owner = raw.split("/")[-2]
        if clone:
            self.fork_path = self.clone_repository(fork)
        self.log_dir = log_dir
        self.logger = self._setup_logger()



    def _setup_logger(self):
        print("Setting up logger")
        os.makedirs(self.log_dir, exist_ok=True)
        log_filename = f'{self.repo_owner}_{self.repo_name}.log'
        log_path = os.path.join(self.log_dir, log_filename)
        logger = logging.getLogger(f'logger_{self.fork_remote_url}')
        logger.setLevel(logging.DEBUG)
        logger.propagate = False  # prevent log duplication in root

        if not logger.handlers:
            handler = logging.FileHandler(log_path)
            formatter = logging.Formatter('%(asctime)s [%(levelname)s] %(message)s')
            handler.setFormatter(formatter)
            logger.addHandler(handler)
        return logger

    def clone_repository(self, repository_url: str) -> str:
        """
        Clones the repository provided and returns its location
        """
        raw = repository_url.replace("https://","").replace("git@", "").replace(".git", "")
        repo_name = raw.split("/")[-1]
        repo_owner = raw.split("/")[-2]
        folder_path = Path(f"{self.workspace}/{repo_owner}")
        repo_path = Path(f"{self.workspace}/{repo_owner}/{repo_name}")
        #Skip if folder already exist
        if repo_path.exists():
            return repo_path
        else :
            repo_path.mkdir(parents=True, exist_ok=True)
            
            subprocess.run(["git", "clone", repository_url], cwd=folder_path)
            return str(repo_path)

    def computeMetadata(self) -> bool:
        # Placeholder
        return False
    


