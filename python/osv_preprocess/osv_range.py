
from enum import Enum
import hashlib


class EventType(str, Enum):
    FIXED = 'FIXED'
    LAST_AFFECTED = 'LAST_AFFECTED'
    INTRODUCED = 'INTRODUCED'
    LIMIT = 'LIMIT'


class OsvRange:
    events: dict[str, EventType]
    vulnerability_id: str
    repo_url: str
    severity: str

    def __init__(
        self,
        vulnerability_id: str,
        repo_url: str,
        severity: str,
        events: dict[str, EventType],
    ):
        self.vulnerability_id = vulnerability_id
        self.repo_url = repo_url
        self.severity = severity
        self.events = {}

        for commit, event_type in events.items():
            if commit.startswith("swh:"):
                commit = self._swhid_to_commit(commit)
            self.events[commit] = event_type


    def _swhid_to_commit(self, swhid: str) -> str:
        return swhid.split(":")[3]
    
    def __eq__(self, other):
        if not isinstance(other, OsvRange):
            return NotImplemented
        return (
            self.vulnerability_id == other.vulnerability_id and
            self.repo_url == other.repo_url and
            self.severity == other.severity and
            self.events == other.events
        )

    def __hash__(self):
        return hash((
            self.vulnerability_id,
            self.repo_url,
            self.severity,
            str(self.events)
        ))
    
    def my_hash(self):
            #Remove typing of event for hashing 
            dict = self.__dict__.copy()
            dict['events'] = {k: v.name for k, v in dict["events"].items()}
            return hashlib.sha256(
                str(dict).encode("utf8")
            ).hexdigest()
    

