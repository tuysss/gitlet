package gitlet;

import java.io.File;
import java.io.Serializable;
import static gitlet.Utils.*;

/**
 *  represent a file object.
 */
public class Blob implements Serializable {
    private String filename;
    private String id;
    private byte[] content;
    private File sourceFile;

    public Blob(String filename, File CWD) {
        this.filename = filename;
        this.sourceFile= join(CWD, filename);
        if (sourceFile.exists()) {
            this.content = readContents(sourceFile);
            this.id = sha1(filename, content);
        } else {
            this.content = null;
            this.id = sha1(filename);
        }
    }

    public boolean exists(){
        return this.content!=null;
    }

    public String getContentAsString(){
        return Utils.readContentsAsString(sourceFile);
    }

    public String getFilename() {
        return filename;
    }

    public String getId() {
        return id;
    }

    public byte[] getContent() {
        return content;
    }
}

