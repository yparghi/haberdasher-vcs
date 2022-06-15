package com.haberdashervcs.common.change;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.haberdashervcs.common.objects.FolderListing;
import com.haberdashervcs.common.objects.FolderWithPath;

import static com.google.common.base.Preconditions.checkNotNull;


public final class Changeset {

    public static final class Builder {

        private final ArrayList<FolderWithPath> changedFolders;
        private final ArrayList<AddChange> addChanges;
        private final ArrayList<DeleteChange> deleteChanges;
        private final ArrayList<ModifyChange> modifyChanges;
        private final ArrayList<RenameChange> renameChanges;
        private Optional<RebaseSpec> rebaseSpec = Optional.empty();

        private Builder() {
            changedFolders = new ArrayList<>();
            addChanges = new ArrayList<>();
            deleteChanges = new ArrayList<>();
            modifyChanges = new ArrayList<>();
            renameChanges = new ArrayList<>();
        }

        public Builder withFolderAndPath(String path, FolderListing listing) {
            changedFolders.add(FolderWithPath.forPathAndListing(path, listing));
            return this;
        }

        public Builder withAddChange(AddChange addChange) {
            addChanges.add(addChange);
            return this;
        }

        public Builder withDeleteChange(DeleteChange deleteChange) {
            deleteChanges.add(deleteChange);
            return this;
        }

        public Builder withModifyChange(ModifyChange modifyChange) {
            modifyChanges.add(modifyChange);
            return this;
        }

        public Builder withRenameChange(RenameChange renameChange) {
            renameChanges.add(renameChange);
            return this;
        }

        public Builder withRebaseSpec(RebaseSpec rebaseSpec) {
            Preconditions.checkState(this.rebaseSpec.isEmpty());
            this.rebaseSpec = Optional.of(rebaseSpec);
            return this;
        }

        public Changeset build() {
            return new Changeset(
                    changedFolders, addChanges, deleteChanges, modifyChanges, renameChanges, rebaseSpec);
        }
    }

    public static Builder builder() {
        return new Builder();
    }


    private final ImmutableList<FolderWithPath> changedFolders;
    private final ImmutableList<AddChange> addChanges;
    private final ImmutableList<DeleteChange> deleteChanges;
    private final ImmutableList<ModifyChange> modifyChanges;
    private final ImmutableList<RenameChange> renameChanges;

    private final String proposedCommitId;
    private final String proposedRootFolderId;

    // Some old notes on rebasing, until I find a better place to keep these:
    //
    // The idea:
    // - Given a branch, with a "base commit" XXX on main...
    // - Make a 2nd copy with duplicate folder history entries, EXCEPT where file id's / folders are changed.
    //    - Assume all files are correct, because the CLIENT will handle conflict resolution/merges.
    // - Update the branch object to point to the 2nd copy, and delete the old one (somehow).
    //
    // Asides:
    // - There's no need to rewrite files, since we'll just content-hash those. The client will send changed files as
    //   needed, and the server can confirm their hashes.
    //
    // Note: This assumes BRANCH COMMIT NUMBERS ARE INCREMENTS, so that we don't have to rewrite e.g. "102" to "118" for
    //     unchanged files in a rebase of +16 on main.
    //
    // TODO!!!!: The TENSION here is that I'm treating merges as sometimes involving multiple commits, and
    //     other times as one big squashed commit...
    //
    // TODO!:
    // ?? LOCK THE BRANCH while pushing/"merging" a rebase?
    //     - Something on the BranchEntry?
    // - Find the folder history tablet for the changed file.
    // - Assume changes in the RebaseSpec are ONLY merge conflict resolutions...
    // - Just add a new folder entry (i.e. appending to the history of the folder) with the updated (resolved) file.
    //     - The "branch-commit" id for these changes will just be +1.
    // - Finally, in ONE MUTATION on the branch entry:
    //     - Bump the head "branch-commit" id.
    //     - Change the base commit ID.
    //
    // TODO: Consider what this means for a merge later on. If I'm just squashing everything on merge anyway,
    //     then it doesn't matter. But what if I'm trying to preserve all those intermediate branch-commits??...
    private final Optional<RebaseSpec> rebaseSpec;


    private Changeset(
            List<FolderWithPath> changedFolders,
            List<AddChange> addChanges,
            List<DeleteChange> deleteChanges,
            List<ModifyChange> modifyChanges,
            List<RenameChange> renameChanges,
            Optional<RebaseSpec> rebaseSpec) {
        this.changedFolders = ImmutableList.copyOf(checkNotNull(changedFolders));
        this.addChanges = ImmutableList.copyOf(checkNotNull(addChanges));
        this.deleteChanges = ImmutableList.copyOf(checkNotNull(deleteChanges));
        this.modifyChanges = ImmutableList.copyOf(checkNotNull(modifyChanges));
        this.renameChanges = ImmutableList.copyOf(checkNotNull(renameChanges));
        this.rebaseSpec = rebaseSpec;

        // TODO where *should* these id's come from?
        this.proposedCommitId = UUID.randomUUID().toString();
        this.proposedRootFolderId = UUID.randomUUID().toString();
    }

    public List<AddChange> getAddChanges() {
        return addChanges;
    }

    public List<DeleteChange> getDeleteChanges() {
        return deleteChanges;
    }

    public List<ModifyChange> getModifyChanges() {
        return modifyChanges;
    }

    public List<RenameChange> getRenameChanges() {
        return renameChanges;
    }

    // TODO some parsing or checking that makes sure when a folder is changed, all of its parent folders on the path
    // have change entries also?
    public List<FolderWithPath> getChangedFolders() {
        return changedFolders;
    }

    public String getProposedCommitId() {
        return proposedCommitId;
    }

    public String getProposedRootFolderId() {
        return proposedRootFolderId;
    }

    public Optional<RebaseSpec> getRebaseSpec() {
        return rebaseSpec;
    }

    public String getDebugString() {
        return "Changeset: " + MoreObjects.toStringHelper(this)
                .add("changedFolders", changedFolders)
                .add("addChanges", addChanges)
                .add("deleteChanges", deleteChanges)
                .add("modifyChanges", modifyChanges)
                .add("renameChanges", renameChanges)
                .add("proposedCommitId", proposedCommitId)
                .add("proposedRootFolderId", proposedRootFolderId)
                .add("rebaseSpec", rebaseSpec)
                .toString();
    }
}
