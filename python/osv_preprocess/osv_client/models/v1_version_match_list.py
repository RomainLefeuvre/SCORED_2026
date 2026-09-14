from typing import TYPE_CHECKING, Any, Dict, List, Type, TypeVar, Union

from attrs import define, field

from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.v1_version_match import V1VersionMatch


T = TypeVar("T", bound="V1VersionMatchList")


@define
class V1VersionMatchList:
    """Result of DetmineVersion.

    Attributes:
        matches (Union[Unset, List['V1VersionMatch']]):
    """

    matches: Union[Unset, List["V1VersionMatch"]] = UNSET
    additional_properties: Dict[str, Any] = field(init=False, factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        matches: Union[Unset, List[Dict[str, Any]]] = UNSET
        if not isinstance(self.matches, Unset):
            matches = []
            for matches_item_data in self.matches:
                matches_item = matches_item_data.to_dict()

                matches.append(matches_item)

        field_dict: Dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update({})
        if matches is not UNSET:
            field_dict["matches"] = matches

        return field_dict

    @classmethod
    def from_dict(cls: Type[T], src_dict: Dict[str, Any]) -> T:
        from ..models.v1_version_match import V1VersionMatch

        d = src_dict.copy()
        matches = []
        _matches = d.pop("matches", UNSET)
        for matches_item_data in _matches or []:
            matches_item = V1VersionMatch.from_dict(matches_item_data)

            matches.append(matches_item)

        v1_version_match_list = cls(
            matches=matches,
        )

        v1_version_match_list.additional_properties = d
        return v1_version_match_list

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
