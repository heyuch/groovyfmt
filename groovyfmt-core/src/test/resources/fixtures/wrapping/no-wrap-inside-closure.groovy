class NoWrapInsideClosure {

    void m(list) {
        list.each { item -> println item.someVeryLongMethodName(firstArgument, secondArgument, thirdArgument, fourthArgument) }
    }

}
