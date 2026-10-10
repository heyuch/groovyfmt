class A {
void m() {
def r = new Runnable() {
void run() {
println 1
}
}
f(new Runnable() {
@Deprecated
void run() {
println 2
}
})
}
}
