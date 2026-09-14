from enum import Enum


class V1VersionRepositoryInformationRepoType(str, Enum):
    GIT = "GIT"
    UNSPECIFIED = "UNSPECIFIED"

    def __str__(self) -> str:
        return str(self.value)
