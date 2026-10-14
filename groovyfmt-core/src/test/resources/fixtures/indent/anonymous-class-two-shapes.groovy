class TwoShapes {

    void m() {
        def trailing = run("some long argument string here", new RowMapper<Map<String, String>>() {
            Map mapRow(ResultSet rs, int rowNum) {
                return [a: 1]
            }
        })
        run("some long argument string that pushes this line well past the limit for sure",
                new RowMapper<Map<String, String>>() {
            Map mapRow(ResultSet rs, int rowNum) {
                return [a: 1]
            }
        })
    }

}
