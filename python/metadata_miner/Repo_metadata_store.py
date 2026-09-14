import fcntl
import logging
from pathlib import Path
from typing import List, Tuple
import json

from osv_preprocess.osv_range import OsvRange

class RepoMetadataStore:
    """
    A class to store fork metadata.Using jsonl files
    """
    def __init__(self, file_path: str,workspace:str,cls):
        self.workspace=workspace
        self.fork_metadata_class = cls
        self.file_path = file_path
        self.fork_metadata = self._load_jsonl(file_path)
        self.fork_metadata = {key: value for item in self.fork_metadata for key, value in item.items()}
        print("init over")
    def get(self, fork_url: str):
       if not fork_url in self.fork_metadata:
           print(f"Fork URL {fork_url} not found in fork metadata. Computing...")
           fork_metadata = self.fork_metadata_class(fork=fork_url,workspace=self.workspace).computeMetadata()
           self.fork_metadata[fork_url] = fork_metadata
           self._add_line_to_jsonl(self.file_path, {fork_url: fork_metadata}) 
    #    else:
    #        print(f"Fork URL {fork_url} found in fork metadata. Using cached value.")
        
       return self.fork_metadata[fork_url]
    
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
