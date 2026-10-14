class InsurerHierarchyDO {

    String hierarchyCode
    @CompareField()
    @MessageResource(messageCode = 'A')
    String businessUnit
    @CompareField()
    @MessageResource(messageCode = 'B')
    String businessUnitNo
    int hierarchyLevel

}
