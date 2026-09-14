from enum import Enum


class OsvRangeType(str, Enum):
    ECOSYSTEM = "ECOSYSTEM"
    GIT = "GIT"
    SEMVER = "SEMVER"
    UNSPECIFIED = "UNSPECIFIED"

    def __str__(self) -> str:
        return str(self.value)
