package app.brokoli5191.picket.privileged;

/**
 * Typed operations of the privileged UserService (runs as shell uid, or root with Sui).
 *
 * There is deliberately no way to run an arbitrary command through this interface. Results are
 * JSON-encoded, gzip-compressed payloads of the models in :core (see PayloadCodec), so every
 * method's result type is fixed by its name.
 */
interface IPicketService {
    /** Reserved by Shizuku: called by the server when the service is unbound for good. */
    void destroy() = 16777114;

    /** Protocol version, bumped whenever a method signature or payload type changes. */
    int protocolVersion() = 1;

    /** uid the service runs as (2000 = shell, 0 = root). */
    int uid() = 2;
}
