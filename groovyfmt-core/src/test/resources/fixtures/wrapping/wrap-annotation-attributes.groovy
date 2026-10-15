class A {

    @Parameter(name = "Authorization", description = "Http Basic authorization token", required = true, in = ParameterIn.HEADER)
    void m() {
        def someVeryLongVariableNameHere = computeSomething(alpha, beta, gamma, delta, epsilon, zeta, eta)
    }

}
