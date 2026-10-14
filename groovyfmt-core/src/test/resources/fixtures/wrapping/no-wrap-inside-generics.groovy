class NoWrapInsideGenerics {

    void m() {
        def result = jdbcTemplate.query("select op_id, tenant_code from tenant_op where status=? and op_type=?", new RowMapper<Map<String, String>>() { })
    }

}
