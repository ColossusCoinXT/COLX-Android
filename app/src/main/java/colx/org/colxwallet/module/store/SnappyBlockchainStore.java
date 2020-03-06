package colx.org.colxwallet.module.store;

import android.support.annotation.Nullable;

import com.snappydb.DB;
import com.snappydb.DBFactory;
import com.snappydb.SnappydbException;

import org.colxj.core.Block;
import org.colxj.core.Context;
import org.colxj.core.NetworkParameters;
import org.colxj.core.Sha256Hash;
import org.colxj.core.StoredBlock;
import org.colxj.store.BlockStore;
import org.colxj.store.BlockStoreException;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import colx.org.colxwallet.LogHelper;
import global.ILogHelper;

/**
 * Created by furszy on 10/17/17.
 */

public class SnappyBlockchainStore implements BlockStore{
    private static final ILogHelper log = LogHelper.getLogHelper(SnappyBlockchainStore.class);
    private static final String CHAIN_HEAD_KEY_STRING = "chainhead";

    private final Context context;
    private DB db;
    private final ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
    private final ByteBuffer zerocoinBuffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE_ZEROCOIN);
    private final File path;
    private final String filename;


    /** Creates a LevelDB SPV block store using the given factory, which is useful if you want a pure Java version. */
    public SnappyBlockchainStore(Context context, File directory,String filename) throws BlockStoreException {
        this.context = context;
        this.path = directory;
        this.filename = filename;
        try {
            log.trace("SnappyBlockchainStore: " + filename);
            tryOpen(directory, filename);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new BlockStoreException(e);
        }
    }

    private synchronized void tryOpen(File directory,String filename) throws IOException, BlockStoreException {
        try {
            log.trace("SnappyBlockchainStore:tryOpen" + filename);
            db = DBFactory.open(directory.getAbsolutePath(),filename);
        } catch (SnappydbException e) {
            log.error(e.getMessage(), e);
            throw new IOException(e);
        }
        initStoreIfNeeded();
    }

    private synchronized void initStoreIfNeeded() throws BlockStoreException {
        try {
            log.trace("SnappyBlockchainStore:initStoreIfNeeded");
            if (db.getBytes(CHAIN_HEAD_KEY_STRING) != null) {
                log.info("SnappyBlockchainStore has already been initialized");
                return;   // Already initialised.
            }
        } catch (SnappydbException e) {
            // not initialized
            log.error(e.getMessage(), e);
            Block genesis = context.getParams().getGenesisBlock().cloneAsHeader();
            StoredBlock storedGenesis = new StoredBlock(genesis, genesis.getWork(), 0);
            put(storedGenesis);
            setChainHead(storedGenesis);
        }
    }

    @Override
    public synchronized void put(StoredBlock block) throws BlockStoreException {
        try {
            //System.out.println("### trying to save something..");
            ByteBuffer buffer;
            buffer = block.getHeader().isZerocoin() ? zerocoinBuffer : this.buffer;
            buffer.clear();
            //System.out.println("Block information: " + block.toString());
            block.serializeCompact(buffer);
            Sha256Hash blockHash = block.getHeader().getHash();
            //System.out.println("### block hash to save: " + blockHash.toString());
            //byte[] hash = blockHash.getBytes();
            byte[] dbBuffer = buffer.array();
            db.put(blockHash.toString(), dbBuffer);
            // just for now to check something:
            StoredBlock dbBlock = get(blockHash);

            assert Arrays.equals(dbBlock.getHeader().getHash().getBytes(), blockHash.getBytes()) : "put is different than get in db.. " + block.getHeader().getHashAsString() + ", db: " + dbBlock.getHeader().getHashAsString();
        } catch (SnappydbException e) {
            log.error(e.getMessage(), e);
            throw new BlockStoreException(e);
        }
    }

    @Override @Nullable
    public synchronized StoredBlock get(Sha256Hash hash) throws BlockStoreException {
        try {
            String blockToGet = hash.toString();
            if (!db.exists(blockToGet)) {
                //log.debug("Block to get doesn't exists: " + blockToGet);
                return null;
            }
            byte[] bits = db.getBytes(blockToGet);
            if (bits == null) {
                //log.debug("Block to get bytes not found: " + blockToGet);
                return null;
            }
            return StoredBlock.deserializeCompact(context.getParams(), ByteBuffer.wrap(bits));
        } catch (SnappydbException e) {
            log.error(String.format("SnappyBlockchainStore::get, %s, hash=%s", e.getMessage(), hash.toString()), e);
            return null;
        }
    }

    @Override
    public synchronized StoredBlock getChainHead() throws BlockStoreException {
        try {
            return get(Sha256Hash.wrap(db.getBytes(CHAIN_HEAD_KEY_STRING)));
        } catch (SnappydbException e) {
            log.error(e.getMessage(), e);
            throw new BlockStoreException(e);
        }
    }

    @Override
    public synchronized void setChainHead(StoredBlock chainHead) throws BlockStoreException {
        try {
            db.put(CHAIN_HEAD_KEY_STRING, chainHead.getHeader().getHash().getBytes());
        } catch (SnappydbException e) {
            log.error(e.getMessage(), e);
            throw new BlockStoreException(e);
        }
    }

    @Override
    public synchronized void close() throws BlockStoreException {
        try {
            log.trace("SnappyBlockchainStore::close");
            db.destroy();
        } catch (SnappydbException e) {
            log.error(e.getMessage(), e);
            throw new BlockStoreException(e);
        }
    }

    @Override
    public NetworkParameters getParams() {
        return context.getParams();
    }
}
