package gitlet;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static gitlet.Utils.*;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/** Represents a gitlet repository.
 *
 *  does at a high level.
 *
 *  @author tuysss
 */
public class Repository {
    /**
     *
     * List all instance variables of the Repository class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided two examples for you.
     */

    /**
     * The current working directory.
     */
    public File CWD;

    public Repository() {
        this.CWD = new File(System.getProperty("user.dir"));
        configDIRS();
    }

    public Repository(String cwd) {
        this.CWD = new File(cwd);
        configDIRS();
    }

    private void configDIRS() {
        this.GITLET_DIR = join(CWD, ".gitlet");
        this.STAGING_DIR = join(GITLET_DIR, "staging");
        this.STAGE = join(GITLET_DIR, "stage");
        this.BLOBS_DIR = join(GITLET_DIR, "blobs");
        this.COMMITS_DIR = join(GITLET_DIR, "commits");
        this.REFS_DIR = join(GITLET_DIR, "refs");
        this.HEADS_DIR = join(REFS_DIR, "heads");
        this.REMOTES_DIR = join(REFS_DIR, "remotes");
        this.HEAD = join(GITLET_DIR, "HEAD");
        this.CONFIG = join(GITLET_DIR, "config");
    }

    /**
     * The .gitlet directory.
     * <p>
     * .gitlet
     * -- staging
     * -- [stage]
     * -- blobs
     * -- commits
     * -- refs
     *  -- heads -> [master][branch name]
     *  -- remotes
     *      -- [remote name] ->[branch name]
     * -- [HEAD]
     * -- [config]
     */
    public File GITLET_DIR;

    /**
     * The staging directory, restores staging Blobs
     */
    public File STAGING_DIR;

    /**
     * The Stage Object
     */
    public File STAGE;

    /**
     * The Objects directory, stores committed blobs & commits
     */
    public File BLOBS_DIR;
    public File COMMITS_DIR;

    /**
     * The branches directory
     */
    public File REFS_DIR;
    public File HEADS_DIR;
    public File REMOTES_DIR;
    /**
     * stores current branch's name if it points to tip
     */
    public File HEAD;
    // Note that in Gitlet, there is no way to be in a detached head state

    public File CONFIG;

    public void init() {
        // Failure cases
        if (GITLET_DIR.exists() && GITLET_DIR.isDirectory()) {
            System.out.println("A Gitlet version-control system already exists in the current directory");
            System.exit(0);
        }

        // create directories
        GITLET_DIR.mkdir();
        STAGING_DIR.mkdir();
        writeObject(STAGE, new Stage());
        BLOBS_DIR.mkdir();
        COMMITS_DIR.mkdir();
        REFS_DIR.mkdir();
        HEADS_DIR.mkdir();
        REMOTES_DIR.mkdir();

        // initial commit
        Commit initialCommit = new Commit();
        writeCommitToFile(initialCommit);
        String id = initialCommit.getId();

        // create branch: master
        String branchName = "master";
        writeContents(HEAD, branchName);
        File master = join(HEADS_DIR, branchName);
        writeContents(master, id);

        // create HEAD
        writeContents(HEAD, branchName);

        writeContents(CONFIG, "");
    }

    /**
     * 1. Staging an already-staged file overwrites the previous entry in the staging area with the new contents.
     * 2. If the current working version of the file is identical to the version in the current commit,
     * do not stage it to be added, and remove it from the staging area if it is already there
     * (as can happen when a file is changed, added, and then changed back to it’s original version).
     * 3. The file will no longer be staged for removal (see gitlet rm), if it was at the time of the command.
     *
     * @param filename
     */
    public void add(String filename) {
        File file = join(CWD, filename);
        if (!file.exists()) {
            System.out.println("File does not exist.");
            System.exit(0);
        }

        Commit head = getHead();
        Stage stage = readStage();
        // blob id
        String headId = head.getBlobs().getOrDefault(filename, "");
        String stageId = stage.getAdded().getOrDefault(filename, "");

        Blob blob = new Blob(filename, CWD);
        String blobId = blob.getId();

        if (blobId.equals(headId)) {
            // no need to add the file
            if (!blobId.equals(stageId)) {
                // del the file from staging
                join(STAGING_DIR, stageId).delete();
                stage.getAdded().remove(stageId);
                stage.getRemoved().remove(filename);
                writeStage(stage);
            }
        } else if (!blobId.equals(stageId)) {
            // update staging
            // del original, add the new version
            if (!stageId.equals("")) {
                join(STAGING_DIR, stageId).delete();
            }

            writeObject(join(STAGING_DIR, blobId), blob);
            // change stage added files
            stage.addFile(filename, blobId);
            writeStage(stage);
        }
    }

    /**
     * Unstage the file if it is currently staged for addition.
     * If the file is tracked in the current commit,
     * stage it for removal and remove the file from the working directory if the user has not already done so
     * (do not remove it unless it is tracked in the current commit).
     * @param filename
     */
    public void rm(String filename){
        File file=join(CWD,filename);

        Commit head=getHead();
        Stage stage = readStage();

        String headId = head.getBlobs().getOrDefault(filename, "");
        String stageId = stage.getAdded().getOrDefault(filename, "");

        if(headId.equals("")&&stageId.equals("")){
            exit("No reason to remove the file.");
        }

        //Unstage the file if it is currently staged for addition.
        if(!stageId.equals("")){
            stage.getAdded().remove(filename);
        }else{
            //stage it for removal.
            stage.getRemoved().add(filename);
        }

        Blob blob=new Blob(filename,CWD);
        String blobId = blob.getId();
        // If the file is tracked in the current commit
        if(blob.exists()&&blobId.equals(headId)){
            //remove the file from the working directory.
            restrictedDelete(file);
        }
        writeStage(stage);
    }

    public void commit(String message){
        if (message.equals("")) {
            exit("Please enter a commit message.");
        }

        Commit head = getHead();
        commitWith(message, List.of(head));
    }

    private void commitWith(String message, List<Commit> parents) {
        Stage stage = readStage();
        // If no files have been staged, abort.
        if (stage.isEmpty()) {
            exit("No changes added to the commit.");
        }

        Commit commit = new Commit(message, parents, stage);
        clearStage(stage);
        writeCommitToFile(commit);

        String commitId = commit.getId();
        String branchName = getHeadBranchName();
        File branch = getBranchFile(branchName);
        writeContents(branch, commitId);
    }


    public void log() {
        StringBuffer sb = new StringBuffer();
        Commit commit = getHead();
        while (commit != null) {
            sb.append(commit.getCommitAsString());
            commit = getCommitFromId(commit.getFirstParentId());
        }

        System.out.print(sb);
    }

    public void global_log() {
        StringBuffer sb = new StringBuffer();
        List<String> filenames = plainFilenamesIn(COMMITS_DIR);
        for (String filename : filenames) {
            Commit commit = getCommitFromId(filename);
            sb.append(commit.getCommitAsString());
        }
        System.out.println(sb);
    }


    public void find(String message){
        StringBuffer sb = new StringBuffer();
        List<String> filenames = plainFilenamesIn(COMMITS_DIR);
        for (String filename : filenames) {
            Commit commit = getCommitFromId(filename);
            if(message.equals(commit.getMessage())){
                sb.append(commit.getId()).append("\n");
            }
        }
        if (sb.length() == 0) {
            System.out.println("Found no commit with that message.");
            System.exit(0);
        }
        System.out.println(sb);
    }

    public void status(){
        StringBuffer sb = new StringBuffer();

        sb.append("=== Branches ===\n");
        String headBranch = readContentsAsString(HEAD);
        List<String> branches = plainFilenamesIn(HEADS_DIR);
        for (String branch : branches) {
            if (branch.equals(headBranch)) {
                sb.append("*" + headBranch + "\n");
            } else {
                sb.append(branch + "\n");
            }
        }
        sb.append("\n");

        Stage stage = readStage();
        sb.append("=== Staged Files ===\n");
        for (String filename : stage.getAdded().keySet()) {
            sb.append(filename + "\n");
        }
        sb.append("\n");

        sb.append("=== Removed Files ===\n");
        for (String filename : stage.getRemoved()) {
            sb.append(filename + "\n");
        }
        sb.append("\n");

        sb.append("=== Modifications Not Staged For Commit ===\n");
        sb.append("\n");

        sb.append("=== Untracked Files ===\n");
        sb.append("\n");

        System.out.println(sb);
    }


    /**
     *  java gitlet.Main checkout -- [file name]
     *
     * Takes the version of the file as it exists in the head commit and puts it in the working directory,
     *  overwriting the version of the file that’s already there if there is one.
     * The new version of the file is not staged.
     * @param filename the file in the head commit
     */
    public void checkoutFile(String filename){
        Commit head = getHead();
        String blobId = head.getBlobs().getOrDefault(filename,"");
        checkoutBlobByBlobId(blobId);
    }

    private void checkoutBlobByBlobId(String blobId){
        if("".equals(blobId)){
            System.out.println("File does not exist in that commit.");
            System.exit(0);
        }
        Blob blobToOverwrite=getBlobFromBlobId(blobId);
        File file=join(CWD, blobToOverwrite.getFilename());
        writeContents(file,blobToOverwrite.getContent());
    }

    private Blob getBlobFromBlobId(String blobId){
        File file=join(BLOBS_DIR,blobId);
        return readObject(file,Blob.class);
    }


    /**
     * java gitlet.Main checkout [commit id] -- [file name]
     *
     * Takes the version of the file as it exists in the commit with the given id,
     * and puts it in the working directory, overwriting the version of the file that’s already there if there is one.
     * The new version of the file is not staged.
     * @param commitId the specific version of commit
     * @param filename
     */
    public void checkoutFileWithCommitId(String commitId,String filename){
        Commit commit = getCommitFromId(commitId);
        if(null==commit){
            System.out.println("No commit with that id exists.");
            System.exit(0);
        }
        String blobId = commit.getBlobs().getOrDefault(filename,"");
        checkoutBlobByBlobId(blobId);
    }


    /**
     *  java gitlet.Main checkout [branch name]
     *
     * 1. Takes all files in the commit at the head of the given branch, and puts them in the working directory, overwriting the versions of the files that are already there if they exist.
     * 2. Also, at the end of this command, the given branch will now be considered the current branch (HEAD).
     * 3. Any files that are tracked in the current branch but are not present in the checked-out branch are deleted.
     * 4. The staging area is cleared, unless the checked-out branch is the current branch.
     * Essentially, checkout branch is switch to currently active head pointer.
     * @param branchName the specific branch
     */
    public void checkoutBranch(String branchName){
        File branchFile = getBranchFile(branchName);
        if(branchFile==null||!branchFile.exists()){
            exit("No such branch exists.");
        }
        String headBranchName = getHeadBranchName();
        if(headBranchName.equals(branchName)){
            exit("No need to checkout the current branch.");
        }

        Commit commitFromGivenBranch = getCommitFromBranchName(branchName);

        // If a working file is untracked in the current branch
        // and would be overwritten by the checkout
        validateUntrackedFile(commitFromGivenBranch.getBlobs());

        clearStage(readStage());
        replaceWorkingPlaceWithCommit(commitFromGivenBranch);

        writeContents(HEAD,branchName);
    }


    /**
     * If a working file is untracked in the current branch
     * and would be overwritten by the checkout/reset,
     * warn and exit.
     */
    private void validateUntrackedFile(Map<String, String> blobs){
        List<String> untrackedFiles = getUntrackedFiles();
        if(untrackedFiles.isEmpty()){
            return;
        }
        for (String filename : untrackedFiles) {
            String blobId = new Blob(filename, CWD).getId();
            String otherId = blobs.getOrDefault(filename, "");
            if (!otherId.equals(blobId)) {
                exit("There is an untracked file in the way; delete it, or add and commit it first.");
            }
        }
    }

    private List<String> getUntrackedFiles() {
        List<String> res = new ArrayList<>();
        List<String> stageFiles = readStage().getStagedFilename();
        Set<String> headFiles = getHead().getBlobs().keySet();
        for (String filename : plainFilenamesIn(CWD)) {
            if (!stageFiles.contains(filename) && !headFiles.contains(filename)) {
                res.add(filename);
            }
        }
        Collections.sort(res);
        return res;
    }


    /**
     * Creates a new branch with the given name, and points it at the current head commit.
     */
    public void branch(String branchName){
        File branch = join(HEADS_DIR, branchName);
        if(branch.exists()){
            exit("A branch with that name already exists.");
        }
        String headCommitId = getHeadCommitId();
        writeContents(branch,headCommitId);
    }

    private String getHeadCommitId() {
        String branchName = getHeadBranchName();
        File file = getBranchFile(branchName);
        return readContentsAsString(file);
    }


    /**
     *  java gitlet.Main rm-branch [branch name]
     * Deletes the branch with the given name.
     * @param branchName
     */
    public void rmBranch(String branchName){
        File toRemove = join(HEADS_DIR, branchName);
        if(!toRemove.exists()){
            exit("A branch with that name does not exist.");
        }
        String headBranch = getHeadBranchName();
        if(headBranch.equals(branchName)){
            exit("Cannot remove the current branch.");
        }
        toRemove.delete();
    }

    /**
     * Usage: java gitlet.Main reset [commit id]
     *
     * Description: Checks out all the files tracked by the given commit.
     *              Removes tracked files that are not present in that commit.
     *              Also moves the current branch’s head to that commit node.
     *  The command is essentially checkout of an arbitrary commit that also changes the current branch head.
     * @param commitId
     */
    public void reset(String commitId){
        Commit commit = getCommitFromId(commitId);
        if(commit==null){
            exit("No commit with that id exists.");
        }
        validateUntrackedFile(commit.getBlobs());

        replaceWorkingPlaceWithCommit(commit);
        clearStage(readStage());

        //moves the current branch’s head to that commit node.
        String headBranchName = getHeadBranchName();
        writeContents(join(HEADS_DIR,headBranchName),commitId);
    }


    private void replaceWorkingPlaceWithCommit(Commit commit) {
        clearWorkingSpace();

        for (Map.Entry<String, String> item : commit.getBlobs().entrySet()) {
            String filename = item.getKey();
            String blobId = item.getValue();
            File file = join(CWD, filename);
            Blob blob = readObject(join(BLOBS_DIR, blobId), Blob.class);

            writeContents(file, blob.getContent());
        }
    }

    private void clearWorkingSpace() {
        File[] files = CWD.listFiles(gitletFliter);
        for (File file : files) {
            delFile(file);
        }
    }

    private void delFile(File file) {
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                delFile(f);
            }
        }
        file.delete();
    }

    private FilenameFilter gitletFliter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
            return !name.equals(".gitlet");
        }
    };


    /**
     * moving all staging dir's blob file to blobs dir.
     *
     * @param stage
     */
    private void clearStage(Stage stage) {
        File[] files = STAGING_DIR.listFiles();
        if (files == null) {
            return;
        }
        Path targetDir = BLOBS_DIR.toPath();
        for (File file : files) {
            Path source = file.toPath();
            try {
                Files.move(source, targetDir.resolve(source.getFileName()), REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        writeStage(new Stage());
    }


    private Commit getHead(){
        String branchName = getHeadBranchName();
        File branchFile = getBranchFile(branchName);
        Commit head = getCommitFromBranchFile(branchFile);

        if (head == null) {
            System.out.println("error! cannot find HEAD!");
            System.exit(0);
        }

        return head;
    }



    private String getHeadBranchName(){
        return readContentsAsString(HEAD);
    }

    private File getBranchFile(String branchName){
        return join(HEADS_DIR,branchName);
    }

    private Commit getCommitFromBranchFile(File branchFile){
        String id = readContentsAsString(branchFile);
        return getCommitFromId(id);
    }

    private Commit getCommitFromBranchName(String branchName) {
        File file = getBranchFile(branchName);
        return getCommitFromBranchFile(file);
    }

    private Commit getCommitFromId(String CommitId){
        File file = join(COMMITS_DIR, CommitId);
        if("null".equals(CommitId)||!file.exists()){
            return null;
        }
        return readObject(file,Commit.class);
    }

    private Stage readStage(){
        return readObject(STAGE,Stage.class);
    }

    private void writeStage(Stage stage){
        writeObject(STAGE,stage);
    }


    private void writeCommitToFile(Commit commit){
         File file=join(COMMITS_DIR,commit.getId());
         writeObject(file,commit);
    }



    /**
     * check things
     */
    void checkIfInitDirectoryExists() {
        if (!GITLET_DIR.isDirectory()) {
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        }
    }

    void checkCommandLength(int actual, int expected) {
        if (actual != expected) {
            messageIncorrectOperands();
        }
    }

    void checkEqual(String actual, String expected) {
        if (!actual.equals(expected)) {
            messageIncorrectOperands();
        }
    }

    void messageIncorrectOperands() {
        System.out.println("Incorrect operands.");
        System.exit(0);
    }

    void exit(String message){
        System.out.println(message);
        System.exit(0);
    }
}
