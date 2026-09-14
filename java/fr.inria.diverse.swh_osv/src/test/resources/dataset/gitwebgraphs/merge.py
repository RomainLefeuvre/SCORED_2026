# Copyright (C) 2021-2022  The Software Heritage developers
# See the AUTHORS file at the top-level directory of this distribution
# License: GNU General Public License version 3, or any later version
# See top-level LICENSE file for more information

"""
This module defines the various objects of the
:ref:`swh.graph example dataset <swh-graph-example-dataset>`.
"""

import datetime
from pathlib import Path
from typing import List, Union

from swh.model.model import (
    Content,
    Directory,
    DirectoryEntry,
    ObjectType,
    Origin,
    OriginVisit,
    OriginVisitStatus,
    Person,
    Release,
    Revision,
    RevisionType,
    SkippedContent,
    Snapshot,
    SnapshotBranch,
    TargetType,
    Timestamp,
    TimestampWithTimezone,
)


def h(id: int, width=40) -> bytes:
    """
    :meta private:
    """
    return bytes.fromhex(f"{id:0{width}}")


PERSONS: List[Person] = [
    Person(fullname=b"foo", name=b"foo", email=b"")
]
"""Example :py:class:`swh.model.model.Person` instances

  Each Person is respectively named “foo“, “bar”, and “baz”.
  They are used as authors in :py:const:`REVISIONS` and :py:const:`RELEASES`.

  :meta hide-value:
"""  # pylint: disable=W0105

CONTENTS: List[Content] = [
    Content(sha1_git=h(1), sha1=h(1), sha256=h(
        1, 64), blake2s256=h(1, 64), length=42)
]
"""Example :py:class:`swh.model.model.Content` instances

  :meta hide-value:

  Objects have the following SWHIDs:

  - ``swh:1:cnt:0000000000000000000000000000000000000001``
  - ``swh:1:cnt:0000000000000000000000000000000000000002``
  - ``swh:1:cnt:0000000000000000000000000000000000000003``
"""  # pylint: disable=W0105
# "\n".join([f"  - {c.swhid()}" for c in CONTENTS])


SKIPPED_CONTENTS: List[SkippedContent] = [
    SkippedContent(
        sha1_git=h(201),
        sha1=h(201),
        sha256=h(201, 64),
        blake2s256=h(201, 64),
        length=404,
        status="absent",
        reason="Not found",
    ),
]
"""Example :py:class:`swh.model.model.SkippedContent` instances

  :meta hide-value:

  Object has the following SWHIDs:

  - ``swh:1:cnt:0000000000000000000000000000000000000201``
"""  # pylint: disable=W0105
# "\n".join([f"  - {c.swhid()}" for c in SKIPPED_CONTENTS])


DIRECTORIES: List[Directory] = [
    Directory(
        id=h(101),
        entries=(
            DirectoryEntry(
                name=b"main.java",
                perms=0o100644,
                type="file",
                target=h(1),
            ),
        ),
    )
]
""" Example :py:class:`swh.model.model.Directory` instances

  Directories have the following SWHIDs and content:

  * ``swh:1:dir:0000000000000000000000000000000000000002``

    - ``main.java`` → ``swh:1:cnt:0000000000000000000000000000000000000001``

  :meta hide-value:
"""  

REVISIONS: List[Revision] = [
    Revision(
        id=h(301),
        message=b"Initial commit",
        date=TimestampWithTimezone(
            timestamp=Timestamp(
                seconds=1111122220,
                microseconds=0,
            ),
            offset_bytes=b"+0200",
        ),
        committer=PERSONS[0],
        author=PERSONS[0],
        committer_date=TimestampWithTimezone(
            timestamp=Timestamp(
                seconds=1111122220,
                microseconds=0,
            ),
            offset_bytes=b"+0200",
        ),
        type=RevisionType.GIT,
        directory=h(101),
        synthetic=False,
        metadata=None,
        parents=(),
    ),
    Revision(
        id=h(302),
        message=b"commit_1",
        date=TimestampWithTimezone(
            timestamp=Timestamp(
                seconds=1111144440,
                microseconds=0,
            ),
            offset_bytes=b"+0200",
        ),
        committer=PERSONS[0],
        author=PERSONS[0],
        committer_date=TimestampWithTimezone(
            timestamp=Timestamp(
                seconds=1111155550,
                microseconds=0,
            ),
            offset_bytes=b"+0200",
        ),
        type=RevisionType.GIT,
        directory=h(101),
        synthetic=False,
        metadata=None,
        parents=(),
    ),
    Revision(
        id=h(303),
        message=b"commit_2",
        date=TimestampWithTimezone(
            timestamp=Timestamp(
                seconds=1111144440,
                microseconds=0,
            ),
            offset_bytes=b"+0200",
        ),
        committer=PERSONS[0],
        author=PERSONS[0],
        committer_date=TimestampWithTimezone(
            timestamp=Timestamp(
                seconds=1111155550,
                microseconds=0,
            ),
            offset_bytes=b"+0200",
        ),
        type=RevisionType.GIT,
        directory=h(101),
        synthetic=False,
        metadata=None,
        parents=(h(302),h(301)),
    ),
    Revision(
        id=h(304),
        message=b"commit_2",
        date=TimestampWithTimezone(
            timestamp=Timestamp(
                seconds=1111144440,
                microseconds=0,
            ),
            offset_bytes=b"+0200",
        ),
        committer=PERSONS[0],
        author=PERSONS[0],
        committer_date=TimestampWithTimezone(
            timestamp=Timestamp(
                seconds=1111155550,
                microseconds=0,
            ),
            offset_bytes=b"+0200",
        ),
        type=RevisionType.GIT,
        directory=h(101),
        synthetic=False,
        metadata=None,
        parents=(h(303),),
    )
]
"""Example :py:class:`swh.model.model.Revision` instances

  :meta hide-value:

  Objects have the following SWHIDs:

  - ``swh:1:rev:0000000000000000000000000000000000000301``
  - ``swh:1:rev:0000000000000000000000000000000000000302``
  - ``swh:1:rev:0000000000000000000000000000000000000303``
"""


RELEASES: List[Release] = [
    Release(
        id=h(401),
        name=b"v1.0",
        date=TimestampWithTimezone(
            timestamp=Timestamp(
                seconds=1234567890,
                microseconds=0,
            ),
            offset_bytes=b"+0200",
        ),
        author=PERSONS[0],
        target_type=ObjectType.REVISION,
        target=h(303),
        message=b"Version 1.0",
        synthetic=False,
    )
]
"""Example :py:class:`swh.model.model.Release` instances

  :meta hide-value:

  Objects have the following SWHIDs:

  - ``swh:1:rel:0000000000000000000000000000000000000401``
"""  # pylint: disable=W0105
# "\n".join([f"  - {r.swhid()}" for r in RELEASES])


SNAPSHOTS: List[Snapshot] = [
    Snapshot(
        id=h(501),
        branches={
            b"refs/heads/master": SnapshotBranch(
                target=h(303), target_type=TargetType.REVISION
            )
        },
    )
]
"""Example :py:class:`swh.model.model.Release` instances

  :meta hide-value:

  Objects have the following SWHIDs and branches:

  * ``swh:1:snp:0000000000000000000000000000000000000501``

    - ``refs/heads/master`` →
      ``swh:1:rev:0000000000000000000000000000000000000301``
    - ``refs/tags/v1.0`` →
      ``swh:1:rel:0000000000000000000000000000000000000401``

"""

ORIGIN_VISITS: List[OriginVisit] = [
    OriginVisit(
        origin="https://example.com/swh/graph",
        date=datetime.datetime(
            2013, 5, 7, 4, 20, 39, 369271, tzinfo=datetime.timezone.utc
        ),
        visit=1,
        type="git",
    )
]
"""Example :py:class:`swh.model.model.OriginVisit` instances

  :meta hide-value:

  Objects have the following origins:

  - ``https://example.com/swh/graph`` (``swh:1:ori:?``)
"""  # pylint: disable=W0105


ORIGIN_VISIT_STATUSES: List[OriginVisitStatus] = [
    OriginVisitStatus(
        origin="https://example.com/swh/graph",
        date=datetime.datetime(
            2013, 5, 7, 4, 20, 41, 369271, tzinfo=datetime.timezone.utc
        ),
        visit=1,
        type="git",
        status="full",
        snapshot=h(501),
        metadata=None,
    )
]
"""Example :py:class:`swh.model.model.OriginVisitStatus` instances

  :meta hide-value:

  Objects have the following origins and snapshots:

  - ``swh:1:ori:?`` →
    ``swh:1:snp:0000000000000000000000000000000000000501``
"""  # pylint: disable=W0105


INITIAL_ORIGIN: Origin = Origin(url="https://example.com/swh/graph")
"""Origin where the development of the tiny project represented by this
example dataset was initially made.

:meta hide-value:

- URL: ``https://example.com/swh/graph``
- SWHID: ``swh:1:ori:?``
"""  # pylint: disable=W0105


ORIGINS: List[Origin] = [INITIAL_ORIGIN]
"""Example :py:class:`swh.model.model.Origin` instances

  :meta hide-value:

  Objects have the following SWHIDs:

  - ``https://example.com/swh/graph`` (``swh:1:ori:83404f995118bd25774f4ac14422a8f175e7a054``)
  - ``https://example.com/swh/graph2`` (``swh:1:ori:8f50d3f60eae370ddbf85c86219c55108a350165``)
"""  # pylint: disable=W0105
# "\n".join([f"  - ``{o.url}`` (``{o.swhid()}``)" for o in ORIGINS]


DATASET_MERGE: List[
    Union[
        Content,
        SkippedContent,
        Directory,
        Revision,
        Release,
        Snapshot,
        OriginVisit,
        OriginVisitStatus,
        Origin,
    ]
] = [
    *CONTENTS,
    *SKIPPED_CONTENTS,
    *DIRECTORIES,
    *REVISIONS,
    *RELEASES,
    *SNAPSHOTS,
    *ORIGIN_VISITS,
    *ORIGIN_VISIT_STATUSES,
    *ORIGINS,
]
"""Full dataset comprised with all the objects defined above.

  :meta hide-value:
"""  # pylint: disable=W0105


DATASET_DIR: Path = Path(__file__).parent
"""Path to the dataset directory

  :meta hide-value:
"""  # pylint: disable=W0105
