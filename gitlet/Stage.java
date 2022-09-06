package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.*;


/**
 *  The stagingArea presentation.
 */
public class Stage implements Serializable {
    /**
     *  added & removed here is referred to the current vision of Commit.
     */
    /** filename-blob's id */
    private Map<String,String> added;
    /** filename */
    private Set<String> removed;

    public Stage() {
        added=new HashMap<>();
        removed=new HashSet<>();
    }

    public void addFile(String filename,String blobId){
        added.put(filename,blobId);
        removed.remove(filename);
    }

    public void removeFile(String filename){
        added.remove(filename);
        removed.add(filename);
    }

    public boolean isEmpty(){
        return added.isEmpty()&&removed.isEmpty();
    }

    public Map<String, String> getAdded() {
        return added;
    }

    public Set<String> getRemoved() {
        return removed;
    }

    public List<String> getStagedFilename(){
        List<String> res=new ArrayList<>();
        res.addAll(added.keySet());
        res.addAll(removed);
        return res;
    }
}
