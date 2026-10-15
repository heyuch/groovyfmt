class AnonymousClassOnContinuation {

    void m() {
        return jdbcTemplate.query("select op_id, tenant_code from tenant_op where status=?",
                new RowMapper<Map<String, String>>() {

                    @Override
                    Map mapRow(ResultSet rs, int rowNum) throws SQLException {
                        return [opId:rs.getString(1), tenantCode:rs.getString(2)]
                    }

                })
    }

}
