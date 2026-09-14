import fcntl
import logging
import os
from pathlib import Path
import threading
from typing import List, Tuple
from concurrent.futures import ThreadPoolExecutor
import json
import subprocess
import uuid
from metadata_miner.archived import ArchivedMetadata
from metadata_miner.cherry_pick import CherryPickMetadata
from metadata_miner.divergence_metadata import DivergenceMetadata
from metadata_miner.Repo_metadata_store import RepoMetadataStore
from osv_preprocess.osv_range import OsvRange
class RangeMetadataStore:
    """
    A class to store archived metadata.Using jsonl files
    """
    def __init__(self, file_folder: str,workspace:str,cls,log_dir: str = "log", read_only: bool = False):
        self.workspace=workspace
        self.log_path = log_dir+"/"+cls.__name__
        self.range_metadata_class = cls
        self.read_only = read_only

        self.file_folder = file_folder
        self.range_metadata = self._load_jsonl(file_folder)
        self.range_metadata = {key: value for item in self.range_metadata for key, value in item.items()}


    def get(self, fork_url: str,range: OsvRange):
       hash= self.get_hash(fork_url,range)
       if not hash in self.range_metadata :
           if self.read_only:
                logging.warning(f"Fork URL {fork_url} {range.vulnerability_id} not found in cached metadata. Read-only mode, cannot compute.")
                return -1
           print(f"Fork URL {fork_url} {range.vulnerability_id} not found in cached metadata. Computing...")
           range_metadata = self.range_metadata_class(range,self.workspace,fork_url,self.log_path).computeMetadata()
           self.range_metadata[hash] = range_metadata
           self._add_line_to_jsonl(self.file_folder, {hash: range_metadata}) 
       #else:
           #logging.warning(f"Fork URL {fork_url} found in cached metadata. Using cached value.")
        
       return self.range_metadata[hash]
    
    
    def get_hash(self,fork_url: str,range: OsvRange):
      return fork_url+"_"+range.vulnerability_id+"_"+str(range.my_hash())

  
    def _add_line_to_jsonl(self,file_path: str, line: dict):
        """
        Append a line to a JSONL file.
        If the file does not exist, it will be created.
        """
        print(f"Adding new entry to JSONL file at {file_path}")
            
        with open(file_path, 'a') as f:
            fcntl.flock(f, fcntl.LOCK_EX)
            json.dump(line, f)
            f.write("\n")
            fcntl.flock(f, fcntl.LOCK_UN)

    def _load_jsonl(self,file_path):
        """
        Load a JSONL file and return a list of dictionaries.
        """
        print(f"Loading JSONL file from {file_path}")
        if not Path(file_path).exists():
            print(f"File {file_path} does not exist. Creating a new one.")
            return []
        with open(file_path, "r") as f:
            return [json.loads(line) for line in f]