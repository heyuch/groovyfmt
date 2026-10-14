class AnnotatedParam {

    @Deprecated
    private String name

    APIResp<RowDTO> updateRow(
            @PathVariable("patternId") String patternId,
            @PathVariable("rowId") String rowId,
            @Valid @RequestBody RowUpdateCmd cmd) {
        @Deprecated
        def local = 1
        return runAndWrap { update(local, cmd) }
    }

}
