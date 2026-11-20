# Shizuku instantiates the UserService reflectively in its own process (by class name, then the
# (Context) or no-arg constructor). R8 must keep the class name and both constructors.
-keep class app.brokoli5191.picket.privileged.service.PicketUserService {
    public <init>();
    public <init>(android.content.Context);
}

# AIDL stubs are referenced statically, but keep the descriptor-bearing interface names stable so
# client and service (same APK) always agree even when the service process outlives a minified build.
-keep class app.brokoli5191.picket.privileged.IPicketService { *; }
-keep class app.brokoli5191.picket.privileged.IPicketService$Stub { *; }
-keep class app.brokoli5191.picket.privileged.IPicketService$Stub$Proxy { *; }
