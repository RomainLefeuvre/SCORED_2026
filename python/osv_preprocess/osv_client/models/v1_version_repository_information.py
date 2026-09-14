from typing import Any, Dict, List, Type, TypeVar, Union

from attrs import define, field

from ..models.v1_version_repository_information_repo_type import V1VersionRepositoryInformationRepoType
from ..types import UNSET, Unset

T = TypeVar("T", bound="V1VersionRepositoryInformation")


@define
class V1VersionRepositoryInformation:
    """
    Attributes:
        type (Union[Unset, V1VersionRepositoryInformationRepoType]):  Default:
            V1VersionRepositoryInformationRepoType.UNSPECIFIED.
        address (Union[Unset, str]): Source address of the repository.
        commit (Union[Unset, str]): Commit hash.
        tag (Union[Unset, str]):
        version (Union[Unset, str]):
    """

    type: Union[Unset, V1VersionRepositoryInformationRepoType] = V1VersionRepositoryInformationRepoType.UNSPECIFIED
    address: Union[Unset, str] = UNSET
    commit: Union[Unset, str] = UNSET
    tag: Union[Unset, str] = UNSET
    version: Union[Unset, str] = UNSET
    additional_properties: Dict[str, Any] = field(init=False, factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        type: Union[Unset, str] = UNSET
        if not isinstance(self.type, Unset):
            type = self.type.value

        address = self.address
        commit = self.commit
        tag = self.tag
        version = self.version

        field_dict: Dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update({})
        if type is not UNSET:
            field_dict["type"] = type
        if address is not UNSET:
            field_dict["address"] = address
        if commit is not UNSET:
            field_dict["commit"] = commit
        if tag is not UNSET:
            field_dict["tag"] = tag
        if version is not UNSET:
            field_dict["version"] = version

        return field_dict

    @classmethod
    def from_dict(cls: Type[T], src_dict: Dict[str, Any]) -> T:
        d = src_dict.copy()
        _type = d.pop("type", UNSET)
        type: Union[Unset, V1VersionRepositoryInformationRepoType]
        if isinstance(_type, Unset):
            type = UNSET
        else:
            type = V1VersionRepositoryInformationRepoType(_type)

        address = d.pop("address", UNSET)

        commit = d.pop("commit", UNSET)

        tag = d.pop("tag", UNSET)

        version = d.pop("version", UNSET)

        v1_version_repository_information = cls(
            type=type,
            address=address,
            commit=commit,
            tag=tag,
            version=version,
        )

        v1_version_repository_information.additional_properties = d
        return v1_version_repository_information

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
