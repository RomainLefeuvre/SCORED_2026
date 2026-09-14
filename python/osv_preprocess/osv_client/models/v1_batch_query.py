from typing import TYPE_CHECKING, Any, Dict, List, Type, TypeVar, Union

from attrs import define, field

from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.v1_query import V1Query


T = TypeVar("T", bound="V1BatchQuery")


@define
class V1BatchQuery:
    """Batch query format.

    Attributes:
        queries (Union[Unset, List['V1Query']]): The queries that form this batch query.
    """

    queries: Union[Unset, List["V1Query"]] = UNSET
    additional_properties: Dict[str, Any] = field(init=False, factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        queries: Union[Unset, List[Dict[str, Any]]] = UNSET
        if not isinstance(self.queries, Unset):
            queries = []
            for queries_item_data in self.queries:
                queries_item = queries_item_data.to_dict()

                queries.append(queries_item)

        field_dict: Dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update({})
        if queries is not UNSET:
            field_dict["queries"] = queries

        return field_dict

    @classmethod
    def from_dict(cls: Type[T], src_dict: Dict[str, Any]) -> T:
        from ..models.v1_query import V1Query

        d = src_dict.copy()
        queries = []
        _queries = d.pop("queries", UNSET)
        for queries_item_data in _queries or []:
            queries_item = V1Query.from_dict(queries_item_data)

            queries.append(queries_item)

        v1_batch_query = cls(
            queries=queries,
        )

        v1_batch_query.additional_properties = d
        return v1_batch_query

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
