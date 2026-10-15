class ClosureArgInMap {

    def build(items) {
        return [
                code:1,
                items:items?.collect {
                    LabelValueBean.of(it.contentCode)
                }
        ]
    }

}
