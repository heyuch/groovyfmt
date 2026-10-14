class BlockStartEnd {

    void withBlanks() {
        println 1
    }

    void withClosure() {
        def c = {
            println 2
        }
        c()
    }

}
