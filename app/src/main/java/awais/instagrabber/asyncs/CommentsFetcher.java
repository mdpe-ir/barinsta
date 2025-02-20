package awais.instagrabber.asyncs;

import android.os.AsyncTask;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import awais.instagrabber.BuildConfig;
import awais.instagrabber.interfaces.FetchListener;
import awais.instagrabber.models.CommentModel;
import awais.instagrabber.repositories.responses.FriendshipStatus;
import awais.instagrabber.repositories.responses.User;
import awais.instagrabber.utils.Constants;
import awais.instagrabber.utils.NetworkUtils;
import awais.instagrabber.utils.TextUtils;
import awaisomereport.LogCollector;

import static awais.instagrabber.utils.Utils.logCollector;

public final class CommentsFetcher extends AsyncTask<Void, Void, List<CommentModel>> {
    private static final String TAG = "CommentsFetcher";

    private final String shortCode, endCursor;
    private final FetchListener<List<CommentModel>> fetchListener;

    public CommentsFetcher(final String shortCode, final String endCursor, final FetchListener<List<CommentModel>> fetchListener) {
        this.shortCode = shortCode;
        this.endCursor = endCursor;
        this.fetchListener = fetchListener;
    }

    @NonNull
    @Override
    protected List<CommentModel> doInBackground(final Void... voids) {
         /*
        "https://www.instagram.com/graphql/query/?query_hash=97b41c52301f77ce508f55e66d17620e&variables=" + "{\"shortcode\":\"" + shortcode + "\",\"first\":50,\"after\":\"" + endCursor + "\"}";

        97b41c52301f77ce508f55e66d17620e -> for comments
        51fdd02b67508306ad4484ff574a0b62 -> for child comments

        https://www.instagram.com/graphql/query/?query_hash=51fdd02b67508306ad4484ff574a0b62&variables={"comment_id":"18100041898085322","first":50,"after":""}
         */
        final List<CommentModel> commentModels = getParentComments();
        if (commentModels != null) {
            for (final CommentModel commentModel : commentModels) {
                final List<CommentModel> childCommentModels = commentModel.getChildCommentModels();
                if (childCommentModels != null) {
                    final int childCommentsLen = childCommentModels.size();
                    final CommentModel lastChild = childCommentModels.get(childCommentsLen - 1);
                    if (lastChild != null && lastChild.hasNextPage() && !TextUtils.isEmpty(lastChild.getEndCursor())) {
                        final List<CommentModel> remoteChildComments = getChildComments(commentModel.getId());
                        commentModel.setChildCommentModels(remoteChildComments);
                        lastChild.setPageCursor(false, null);
                    }
                }
            }
        }
        return commentModels;
    }

    @Override
    protected void onPreExecute() {
        if (fetchListener != null) fetchListener.doBefore();
    }

    @Override
    protected void onPostExecute(final List<CommentModel> result) {
        if (fetchListener != null) fetchListener.onResult(result);
    }

    @NonNull
    private synchronized List<CommentModel> getChildComments(final String commentId) {
        final List<CommentModel> commentModels = new ArrayList<>();
        String childEndCursor = "";
        while (childEndCursor != null) {
            final String url = "https://www.instagram.com/graphql/query/?query_hash=51fdd02b67508306ad4484ff574a0b62&variables=" +
                    "{\"comment_id\":\"" + commentId + "\",\"first\":50,\"after\":\"" + childEndCursor + "\"}";
            try {
                final HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setUseCaches(false);
                conn.connect();

                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) break;
                else {
                    final JSONObject data = new JSONObject(NetworkUtils.readFromConnection(conn)).getJSONObject("data")
                                                                                                 .getJSONObject("comment")
                                                                                                 .getJSONObject("edge_threaded_comments");

                    final JSONObject pageInfo = data.getJSONObject("page_info");
                    childEndCursor = pageInfo.getString("end_cursor");
                    if (TextUtils.isEmpty(childEndCursor)) childEndCursor = null;

                    final JSONArray childComments = data.optJSONArray("edges");
                    if (childComments != null) {
                        final int length = childComments.length();
                        for (int i = 0; i < length; ++i) {
                            final JSONObject childComment = childComments.getJSONObject(i).optJSONObject("node");

                            if (childComment != null) {
                                final JSONObject owner = childComment.getJSONObject("owner");
                                final User user = new User(
                                        owner.optLong(Constants.EXTRAS_ID, 0),
                                        owner.getString(Constants.EXTRAS_USERNAME),
                                        null,
                                        false,
                                        owner.getString("profile_pic_url"),
                                        null,
                                        new FriendshipStatus(false, false, false, false, false, false, false, false, false, false),
                                        false, false, false, false, false, null, null, 0, 0, 0, 0, null, null, 0, null, null, null,
                                        null, null);
                                final JSONObject likedBy = childComment.optJSONObject("edge_liked_by");
                                commentModels.add(new CommentModel(childComment.getString(Constants.EXTRAS_ID),
                                                                   childComment.getString("text"),
                                                                   childComment.getLong("created_at"),
                                                                   likedBy != null ? likedBy.optLong("count", 0) : 0,
                                                                   childComment.getBoolean("viewer_has_liked"),
                                                                   user));
                            }
                        }
                    }
                }
                conn.disconnect();
            } catch (final Exception e) {
                if (logCollector != null)
                    logCollector.appendException(e,
                                                 LogCollector.LogFile.ASYNC_COMMENTS_FETCHER,
                                                 "getChildComments",
                                                 new Pair<>("commentModels.size", commentModels.size()));
                if (BuildConfig.DEBUG) Log.e(TAG, "", e);
                if (fetchListener != null) fetchListener.onFailure(e);
                break;
            }
        }

        return commentModels;
    }

    @NonNull
    private synchronized List<CommentModel> getParentComments() {
        final List<CommentModel> commentModels = new ArrayList<>();
        final String url = "https://www.instagram.com/graphql/query/?query_hash=bc3296d1ce80a24b1b6e40b1e72903f5&variables=" +
                "{\"shortcode\":\"" + shortCode + "\",\"first\":50,\"after\":\"" + endCursor.replace("\"", "\\\"") + "\"}";
        try {
            final HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setUseCaches(false);
            conn.connect();

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            else {
                final JSONObject parentComments = new JSONObject(NetworkUtils.readFromConnection(conn)).getJSONObject("data")
                                                                                                       .getJSONObject("shortcode_media")
                                                                                                       .getJSONObject(
                                                                                                               "edge_media_to_parent_comment");

                final JSONObject pageInfo = parentComments.getJSONObject("page_info");
                final String foundEndCursor = pageInfo.optString("end_cursor");
                final boolean hasNextPage = pageInfo.optBoolean("has_next_page", !TextUtils.isEmpty(foundEndCursor));

                // final boolean containsToken = endCursor.contains("bifilter_token");
                // if (!Utils.isEmpty(endCursor) && (containsToken || endCursor.contains("cached_comments_cursor"))) {
                //     final JSONObject endCursorObject = new JSONObject(endCursor);
                //     endCursor = endCursorObject.optString("cached_comments_cursor");
                //
                //     if (!Utils.isEmpty(endCursor))
                //         endCursor = "{\\\"cached_comments_cursor\\\": \\\"" + endCursor + "\\\", ";
                //     else
                //         endCursor = "{";
                //
                //     endCursor = endCursor + "\\\"bifilter_token\\\": \\\"" + endCursorObject.getString("bifilter_token") + "\\\"}";
                // }
                // else if (containsToken) endCursor = null;

                final JSONArray comments = parentComments.getJSONArray("edges");
                final int commentsLen = comments.length();
                for (int i = 0; i < commentsLen; ++i) {
                    final JSONObject comment = comments.getJSONObject(i).getJSONObject("node");

                    final JSONObject owner = comment.getJSONObject("owner");
                    final User user = new User(
                            owner.optLong(Constants.EXTRAS_ID, 0),
                            owner.getString(Constants.EXTRAS_USERNAME),
                            null,
                            false,
                            owner.getString("profile_pic_url"),
                            null,
                            new FriendshipStatus(false, false, false, false, false, false, false, false, false, false),
                            owner.optBoolean("is_verified"),
                            false, false, false, false, null, null, 0, 0, 0, 0, null, null, 0, null, null, null, null,
                            null);
                    final JSONObject likedBy = comment.optJSONObject("edge_liked_by");
                    final String commentId = comment.getString(Constants.EXTRAS_ID);
                    final CommentModel commentModel = new CommentModel(commentId,
                                                                       comment.getString("text"),
                                                                       comment.getLong("created_at"),
                                                                       likedBy != null ? likedBy.optLong("count", 0) : 0,
                                                                       comment.getBoolean("viewer_has_liked"),
                                                                       user);
                    if (i == 0 && !foundEndCursor.contains("tao_cursor"))
                        commentModel.setPageCursor(hasNextPage, TextUtils.isEmpty(foundEndCursor) ? null : foundEndCursor);
                    JSONObject tempJsonObject;
                    final JSONArray childCommentsArray;
                    final int childCommentsLen;
                    if ((tempJsonObject = comment.optJSONObject("edge_threaded_comments")) != null &&
                            (childCommentsArray = tempJsonObject.optJSONArray("edges")) != null
                            && (childCommentsLen = childCommentsArray.length()) > 0) {

                        final String childEndCursor;
                        final boolean childHasNextPage;
                        if ((tempJsonObject = tempJsonObject.optJSONObject("page_info")) != null) {
                            childEndCursor = tempJsonObject.optString("end_cursor");
                            childHasNextPage = tempJsonObject.optBoolean("has_next_page", !TextUtils.isEmpty(childEndCursor));
                        } else {
                            childEndCursor = null;
                            childHasNextPage = false;
                        }

                        final List<CommentModel> childCommentModels = new ArrayList<>();
                        for (int j = 0; j < childCommentsLen; ++j) {
                            final JSONObject childComment = childCommentsArray.getJSONObject(j).getJSONObject("node");

                            tempJsonObject = childComment.getJSONObject("owner");
                            final User childUser = new User(
                                    tempJsonObject.optLong(Constants.EXTRAS_ID, 0),
                                    tempJsonObject.getString(Constants.EXTRAS_USERNAME),
                                    null,
                                    false,
                                    tempJsonObject.getString("profile_pic_url"),
                                    null,
                                    new FriendshipStatus(false, false, false, false, false, false, false, false, false, false),
                                    tempJsonObject.optBoolean("is_verified"), false, false, false, false, null, null, 0, 0, 0, 0, null, null, 0,
                                    null, null, null, null, null);

                            tempJsonObject = childComment.optJSONObject("edge_liked_by");
                            childCommentModels.add(new CommentModel(childComment.getString(Constants.EXTRAS_ID),
                                                                    childComment.getString("text"),
                                                                    childComment.getLong("created_at"),
                                                                    tempJsonObject != null ? tempJsonObject.optLong("count", 0) : 0,
                                                                    childComment.getBoolean("viewer_has_liked"),
                                                                    childUser));
                        }
                        childCommentModels.get(childCommentsLen - 1).setPageCursor(childHasNextPage, childEndCursor);
                        commentModel.setChildCommentModels(childCommentModels);
                    }
                    commentModels.add(commentModel);
                }
            }

            conn.disconnect();
        } catch (final Exception e) {
            if (logCollector != null)
                logCollector.appendException(e, LogCollector.LogFile.ASYNC_COMMENTS_FETCHER, "getParentComments",
                                             new Pair<>("commentModelsList.size", commentModels.size()));
            if (BuildConfig.DEBUG) Log.e("AWAISKING_APP", "", e);
            if (fetchListener != null) fetchListener.onFailure(e);
            return null;
        }
        return commentModels;
    }
}
