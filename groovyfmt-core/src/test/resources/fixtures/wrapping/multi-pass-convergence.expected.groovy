class MultiPassConvergence {

    void m(alpha, beta, gamma, delta, epsilon, zeta) {
        def result =
                builder.firstStep(alpha).secondStep(beta).thirdStep(gamma).fourthStep(delta).fifthStep(epsilon)
                .sixthStep(zeta)
    }

}
