class AnnotatedField {

    @Deprecated
    private static final long serialVersionUID = 1L
    @Deprecated

    @SuppressWarnings('unused')
    private String name

    @Deprecated
    void annotatedMethod() {
        println name
    }

}
