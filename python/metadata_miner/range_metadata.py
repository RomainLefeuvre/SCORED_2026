from pathlib import Path
import shutil
from typing import List, Tuple
from concurrent.futures import ThreadPoolExecutor
import subprocess
import sys

from metadata_miner.fork_metadata import ForkMetadata
from osv_preprocess.osv_range import OsvRange
import os
import logging

class RangeMetadata(ForkMetadata):
  
    def __init__(self,workspace:str, range: OsvRange, fork: str,log_dir: str = "log",):

        self.range = range
        range_repo = self.range.repo_url.replace("https://","").replace("git@", "").replace(".git", "")
        self.range_repo_name = range_repo.split("/")[-1]
        self.range_repo_owner = range_repo.split("/")[-2]
        super().__init__(fork=fork,workspace=workspace,log_dir=log_dir)
        self.upstream_path = self.clone_repository(self.range.repo_url)




    def clone_repository(self, repository_url: str) -> str:
        """
        Clones the repository provided and returns its location.
        If the repository already exists, checks with `git status`.
        If `git status` fails, the repo is deleted and re-cloned.
        Includes safety checks to avoid deleting critical paths.
        """
        raw = repository_url.replace("https://", "").replace("git@", "").replace(".git", "")
        repo_name = raw.split("/")[-1].strip()
        repo_owner = raw.split("/")[-2].strip()

        if not repo_name or not repo_owner:
            raise ValueError("Invalid repository URL — repo name or owner is empty.")

        folder_path = Path(f"{self.workspace}/{repo_owner}")
        repo_path = folder_path / repo_name

        if repo_path.exists():
            try:
                # Verify repository health
                subprocess.run(
                    ["git", "status"],
                    cwd=repo_path,
                    check=True,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE
                )
                return str(repo_path)
            except subprocess.CalledProcessError:
                # Extra safety: ensure we’re not deleting root or an unsafe path
                if repo_path.resolve() == Path("/"):
                    raise RuntimeError("Refusing to delete root directory '/'.")

                if not folder_path.name or not repo_name:
                    raise RuntimeError("Invalid repo path detected, aborting delete.")

                logging.warning(f"Repository at {repo_path} is corrupted. Deleting and re-cloning...")
                shutil.rmtree(repo_path)

        # Ensure parent folder exists
        folder_path.mkdir(parents=True, exist_ok=True)
        subprocess.run(["git", "clone", repository_url], cwd=folder_path, check=True)
        return str(repo_path)
    def _setup_logger(self):
      
            print("Setting up logger range")
            os.makedirs(self.log_dir, exist_ok=True)
            id=f"{self.repo_owner}_{self.repo_name}__{self.range_repo_name}_{self.range_repo_name}_{str(self.range.vulnerability_id)}_{str(self.range.my_hash())}"
            log_filename = f'{id}.log'
            log_path = os.path.join(self.log_dir, log_filename)

            print(os.path.abspath(log_path))

            logger = logging.getLogger(f'logger_{id}')
            logger.setLevel(logging.DEBUG)
            logger.propagate = False  # prevent log duplication in root

            if not logger.handlers:
                handler = logging.FileHandler(log_path)
                formatter = logging.Formatter('%(asctime)s [%(levelname)s] %(message)s')
                handler.setFormatter(formatter)
                logger.addHandler(handler)
                logger.info("Set")

        
            return logger
    
    def computeMetadata(self) -> bool:
        # Placeholder
        return False


