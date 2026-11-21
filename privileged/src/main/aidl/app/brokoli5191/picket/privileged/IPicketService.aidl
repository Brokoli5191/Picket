package app.brokoli5191.picket.privileged;

/**
 * Typed operations of the privileged UserService (runs as shell uid, or root with Sui).
 *
 * There is deliberately no way to run an arbitrary command through this interface. Results are
 * gzip-compressed JSON of a Reply<T> from :core (see PayloadCodec); the result type T of each
 * method is fixed by its name and documented here.
 *
 * Only the Picket app itself may call these methods; the service checks the caller's uid.
 */
interface IPicketService {
    /** Reserved by Shizuku: called by the server when the service is unbound for good. */
    void destroy() = 16777114;

    /** Must equal PayloadCodec.PROTOCOL_VERSION of the calling app. */
    int protocolVersion() = 1;

    /** uid the service runs as (2000 = shell, 0 = root). */
    int uid() = 2;

    /** SELinux context of the service process, e.g. "u:r:shell:s0". */
    String seLinuxContext() = 3;

    /** Reply<ProcessSnapshot>: one sample of every process, read from /proc. */
    byte[] listProcesses() = 4;
}
