package dev.onepieceapi.contentservice.domain.workflow;

/**
 * What can be done to a version (docs/user-flows/content-editorial-workflow.md 4.2). A
 * stable contract: a version in a response lists the actions its caller may perform, and
 * the client offers exactly those.
 */
public enum VersionAction {

	/** Change what a draft says. */
	EDIT,

	/** Remove a draft for good. */
	DELETE,

	/** Send a draft to review. */
	SUBMIT,

	/** Take a submitted version back to draft, while nobody holds it. */
	PULL_BACK,

	/** Take a version in review, becoming the only one who can decide on it. */
	CLAIM,

	/** Give up a claim, leaving the version to any reviewer. */
	RELEASE,

	/** Pass the review. */
	APPROVE,

	/** Fail the review, with a reason. */
	REJECT,

	/** Take a rejected version back to draft, the only way to change it. */
	RETURN_TO_DRAFT,

	/** Put a version online; the one that was online is superseded. */
	PUBLISH,

	/** Set an approved version aside without putting it online. */
	ARCHIVE,

	/** Bring an archived version back to ready to publish. */
	RECOVER,

	/** Take the online version down. */
	RETIRE,

	/** Put a version that was online back online, as it was. */
	RESTORE,

	/** Open a new draft of the content, pre-filled from this version. */
	OPEN_NEW_VERSION

}
