from typing import TYPE_CHECKING, Any, Dict, List, Type, TypeVar, Union

from attrs import define, field

from ..models.osv_range_type import OsvRangeType
from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.osv_event import OsvEvent


T = TypeVar("T", bound="OsvRange")


@define
class OsvRange:
    """Affected ranges.

    Attributes:
        type (Union[Unset, OsvRangeType]): Type of the version information. Default: OsvRangeType.UNSPECIFIED.
        repo (Union[Unset, str]): Required if type is GIT. The publicly accessible URL of the repo that can
            be directly passed to clone commands.
        events (Union[Unset, List['OsvEvent']]): Required. Version event information.
    """

    type: Union[Unset, OsvRangeType] = OsvRangeType.UNSPECIFIED
    repo: Union[Unset, str] = UNSET
    events: Union[Unset, List["OsvEvent"]] = UNSET
    additional_properties: Dict[str, Any] = field(init=False, factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        type: Union[Unset, str] = UNSET
        if not isinstance(self.type, Unset):
            type = self.type.value

        repo = self.repo
        events: Union[Unset, List[Dict[str, Any]]] = UNSET
        if not isinstance(self.events, Unset):
            events = []
            for events_item_data in self.events:
                events_item = events_item_data.to_dict()

                events.append(events_item)

        field_dict: Dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update({})
        if type is not UNSET:
            field_dict["type"] = type
        if repo is not UNSET:
            field_dict["repo"] = repo
        if events is not UNSET:
            field_dict["events"] = events

        return field_dict

    @classmethod
    def from_dict(cls: Type[T], src_dict: Dict[str, Any]) -> T:
        from ..models.osv_event import OsvEvent

        d = src_dict.copy()
        _type = d.pop("type", UNSET)
        type: Union[Unset, OsvRangeType]
        if isinstance(_type, Unset):
            type = UNSET
        else:
            type = OsvRangeType(_type)

        repo = d.pop("repo", UNSET)

        events = []
        _events = d.pop("events", UNSET)
        for events_item_data in _events or []:
            events_item = OsvEvent.from_dict(events_item_data)

            events.append(events_item)

        osv_range = cls(
            type=type,
            repo=repo,
            events=events,
        )

        osv_range.additional_properties = d
        return osv_range

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
