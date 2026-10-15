class MultiLineParams {

    private static OutputHfRenderUnit buildRenderUnit(OutputStepDO outputStep,
            OutputHfRenderProfile renderProfile) {
        OutputHfStepDomain stepDomain = new OutputHfStepDomain(
                stepDomainId:outputStep.stepId,
                boundaryStep:outputStep
        )
        return new OutputHfRenderUnit(
                sourceStep:outputStep,
                stepDomain:stepDomain
        )
    }

    private void single(String a) {
        println a
    }

}
