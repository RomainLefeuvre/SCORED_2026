from typing import TYPE_CHECKING, Any, Dict, List, Type, TypeVar, Union

from attrs import define, field

from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.osv_package import OsvPackage
    from ..models.v1_version_repository_information import V1VersionRepositoryInformation


T = TypeVar("T", bound="V1VersionMatch")


@define
class V1VersionMatch:
    """Match information for the provided VersionQuery.

    Attributes:
        score (Union[Unset, float]): Score in the interval (0.0, 1.0] with 1.0 being a perfect match.
        repo_info (Union[Unset, V1VersionRepositoryInformation]):
        osv_identifier (Union[Unset, OsvPackage]): Package information and version.
        cpe23 (Union[Unset, str]): CPE 2.3.
    """

    score: Union[Unset, float] = UNSET
    repo_info: Union[Unset, "V1VersionRepositoryInformation"] = UNSET
    osv_identifier: Union[Unset, "OsvPackage"] = UNSET
    cpe23: Union[Unset, str] = UNSET
    additional_properties: Dict[str, Any] = field(init=False, factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        score = self.score
        repo_info: Union[Unset, Dict[str, Any]] = UNSET
        if not isinstance(self.repo_info, Unset):
            repo_info = self.repo_info.to_dict()

        osv_identifier: Union[Unset, Dict[str, Any]] = UNSET
        if not isinstance(self.osv_identifier, Unset):
            osv_identifier = self.osv_identifier.to_dict()

        cpe23 = self.cpe23

        field_dict: Dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update({})
        if score is not UNSET:
            field_dict["score"] = score
        if repo_info is not UNSET:
            field_dict["repoInfo"] = repo_info
        if osv_identifier is not UNSET:
            field_dict["osvIdentifier"] = osv_identifier
        if cpe23 is not UNSET:
            field_dict["cpe23"] = cpe23

        return field_dict

    @classmethod
    def from_dict(cls: Type[T], src_dict: Dict[str, Any]) -> T:
        from ..models.osv_package import OsvPackage
        from ..models.v1_version_repository_information import V1VersionRepositoryInformation

        d = src_dict.copy()
        score = d.pop("score", UNSET)

        _repo_info = d.pop("repoInfo", UNSET)
        repo_info: Union[Unset, V1VersionRepositoryInformation]
        if isinstance(_repo_info, Unset):
            repo_info = UNSET
        else:
            repo_info = V1VersionRepositoryInformation.from_dict(_repo_info)

        _osv_identifier = d.pop("osvIdentifier", UNSET)
        osv_identifier: Union[Unset, OsvPackage]
        if isinstance(_osv_identifier, Unset):
            osv_identifier = UNSET
        else:
            osv_identifier = OsvPackage.from_dict(_osv_identifier)

        cpe23 = d.pop("cpe23", UNSET)

        v1_version_match = cls(
            score=score,
            repo_info=repo_info,
            osv_identifier=osv_identifier,
            cpe23=cpe23,
        )

        v1_version_match.additional_properties = d
        return v1_version_match

    @property
    def additional_keys(self) -> List[str]:
        return list(self.additional_properties.keys())

    def __getitem__(self, key: str) -> Any:
        return self.additional_properties[key]

    def __setitem__(self, key: str, value: Any) -> None:
        self.additional_properties[key] = value

    def __delitem__(self, key: str) -> None:
        del self.additional_properties[key]

    def __contains__(self, key: str) -> bool:
        return key in self.additional_properties
