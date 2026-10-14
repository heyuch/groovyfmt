class TenantOpQueryServiceImpl {

    @Override
    List<Map<String, String>> getApprovedTenantDataInitialisationTaskIds() {
        return jdbcTemplate.query("select op_id " +
                "from tenant_op where status=?", new RowMapper<Map<String, String>>() {
            @Override
            Map mapRow(ResultSet rs, int rowNum) throws SQLException {
                return [opId: rs.getString(1), tenantCode: rs.getString(2)]
            }
        }, TenantConst.TENANT_OP_STATUS_APPROVED)
    }

}
