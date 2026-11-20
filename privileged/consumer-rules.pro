# Shizuku instantiates the UserService reflectively in its own process: it loads the class by name
# and calls the (Context) constructor. R8 must keep both the class name and that constructor.
-keep class app.brokoli5191.picket.privileged.service.PicketUserService {
    public <init>(android.content.Context);
}

# Client and service are built from the same APK, but the service process may outlive an update of
# a minified build; keep the binder interface names stable so transactions still match.
-keep class app.brokoli5191.picket.privileged.IPicketService { *; }
-keep class app.brokoli5191.picket.privileged.IPicketService$Stub { *; }
-keep class app.brokoli5191.picket.privileged.IPicketService$Stub$Proxy { *; }
