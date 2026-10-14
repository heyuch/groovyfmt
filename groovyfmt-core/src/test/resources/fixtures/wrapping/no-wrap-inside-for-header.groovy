class NoWrapInsideForHeader {

    void m(collection) {
        for (int index = 0; index < someVeryLongCollectionSizeExpressionProvider.valueOf(collection); index++) {
            println index
        }
    }

}
