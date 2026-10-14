class ClosureArrowThenBody {

    void m(list) {
        Closure queryPageDataClosure = { List<String> idList, String tenantCode ->
            List<String> poList = load(idList, tenantCode)
            return poList
        }
        run(queryPageDataClosure)
    }

}
