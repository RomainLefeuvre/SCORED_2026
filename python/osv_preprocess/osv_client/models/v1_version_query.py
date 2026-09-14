from typing import TYPE_CHECKING, Any, Dict, List, Type, TypeVar, Union

from attrs import define, field

from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.v1_file_hash import V1FileHash


T = TypeVar("T", bound="V1VersionQuery")


@define
class V1VersionQuery:
    """The version query.

    Attributes:
        name (Union[Unset, str]): The name of the dependency. Can be empty.
        file_hashes (Union[Unset, List['V1FileHash']]):
    """

    name: Union[Unset, str] = UNSET
    file_hashes: Union[Unset, List["V1FileHash"]] = UNSET
    additional_properties: Dict[str, Any] = field(init=False, factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        name = self.name
        file_hashes: Union[Unset, List[Dict[str, Any]]] = UNSET
        if not isinstance(self.file_hashes, Unset):
            file_hashes = []
            for file_hashes_item_data in self.file_hashes:
                file_hashes_item = file_hashes_item_data.to_dict()

                file_hashes.append(file_hashes_item)

        field_dict: Dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update({})
        if name is not UNSET:
            field_dict["name"] = name
        if file_hashes is not UNSET:
            field_dict["fileHashes"] = file_hashes

        return field_dict

    @classmethod
    def from_dict(cls: Type[T], src_dict: Dict[str, Any]) -> T:
        from ..models.v1_file_hash import V1FileHash

        d = src_dict.copy()
        name = d.pop("name", UNSET)

        file_hashes = []
        _file_hashes = d.pop("fileHashes", UNSET)
        for file_hashes_item_data in _file_hashes or []:
            file_hashes_item = V1FileHash.from_dict(file_hashes_item_data)

            file_hashes.append(file_hashes_item)

        v1_version_query = cls(
            name=name,
            file_hashes=file_hashes,
        )

        v1_version_query.additional_properties = d
        return v1_version_query

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
