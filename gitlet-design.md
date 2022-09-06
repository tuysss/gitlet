## Persistence

```
.gitlet
	-- staging
	-- [stage]
	-- blobs
	-- commits
	-- refs
		-- heads -> [master][branch name]
		-- remotes
			-- [remote git repo name] -> [master][branch name]
	-- [HEAD]
	-- [FETCH_HEAD]
```

+ `staging` directory : stores staged(added) blob file; name is blob id, content is the Blob object.
+ `stage` file: stores Stage object.
+ `blobs` directory: stores all tracked(committed) file; name is blob id, content is the Blob object.
+ `commits` directory: stores all commits; name is commit id, content is the Commit object.
+ `heads` directory in `refs` : stores different branch; name is branch name, content is the commit id on the tip of the branch.
+ `remotes` directory in `refs`: stores different remote repo directory.
+ `HEAD` file: stores current branch's name if it points to tip.
+ `config` file: remote git name & url.
