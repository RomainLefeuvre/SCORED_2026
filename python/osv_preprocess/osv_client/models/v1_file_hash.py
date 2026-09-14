from typing import Any, Dict, List, Type, TypeVar, Union

from attrs import define, field

from ..types import UNSET, Unset

T = TypeVar("T", bound="V1FileHash")


@define
class V1FileHash:
    """Information about the files in the repository
    to identify the version.

        Attributes:
            file_path (Union[Unset, str]): The file path inside the repository, relative to the repository root.
            hash_type (Union[Unset, str]):
            hash_ (Union[Unset, str]):
    """

    file_path: Union[Unset, str] = UNSET
    hash_type: Union[Unset, str] = UNSET
    hash_: Union[Unset, str] = UNSET
    additional_properties: Dict[str, Any] = field(init=False, factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        file_path = self.file_path
        hash_type = self.hash_type
        hash_ = self.hash_

        field_dict: Dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update({})
        if file_path is not UNSET:
            field_dict["filePath"] = file_path
        if hash_type is not UNSET:
            field_dict["hashType"] = hash_type
        if hash_ is not UNSET:
            field_dict["hash"] = hash_

        return field_dict

    @classmethod
    def from_dict(cls: Type[T], src_dict: Dict[str, Any]) -> T:
        d = src_dict.copy()
        file_path = d.pop("filePath", UNSET)

        hash_type = d.pop("hashType", UNSET)

        hash_ = d.pop("hash", UNSET)

        v1_file_hash = cls(
            file_path=file_path,
            hash_type=hash_type,
            hash_=hash_,
        )

        v1_file_hash.additional_properties = d
        return v1_file_hash

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
