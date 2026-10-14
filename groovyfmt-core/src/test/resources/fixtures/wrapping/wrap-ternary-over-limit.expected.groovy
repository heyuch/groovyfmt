class WrapTernaryOverLimit {

    void m(flag) {
        def message = flag ? "the header pattern is invalid and should be corrected"
                : "the footer pattern is invalid and should be corrected"
    }

}
