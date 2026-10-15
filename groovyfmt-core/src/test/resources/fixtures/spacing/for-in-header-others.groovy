class ForInHeaderOthers {

    def pick(items, flag) {
        for (x in [a:1]) {
            println x
        }
        for (Map.Entry e : map.entrySet()) {
            println e
        }
        for (y in (flag ? items : [])) {
            println y
        }
    }

}
