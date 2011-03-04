/*
 * Copyright 2011 Azwan Adli Abdullah
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gh4a;

import java.lang.ref.WeakReference;
import java.util.List;

import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

import com.gh4a.utils.ImageDownloader;
import com.gh4a.utils.StringUtils;
import com.github.api.v2.schema.Organization;
import com.github.api.v2.schema.User;
import com.github.api.v2.services.GitHubException;
import com.github.api.v2.services.GitHubServiceFactory;
import com.github.api.v2.services.OrganizationService;
import com.github.api.v2.services.UserService;
import com.github.api.v2.services.auth.Authentication;
import com.github.api.v2.services.auth.LoginPasswordAuthentication;

/**
 * The UserInfo activity.
 */
public class UserActivity extends BaseActivity implements OnClickListener, OnItemClickListener {

    /** The user login. */
    protected String mUserLogin;

    /** The user name. */
    protected String mUserName;

    /** The watched repos count. */
    protected int mWatchedReposCount;

    /** The loading dialog. */
    protected LoadingDialog mLoadingDialog;
    
    /** The user. */
    protected User mUser;
    
    /**
     * Called when the activity is first created.
     * 
     * @param savedInstanceState the saved instance state
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.user);
        setUpActionBar();

        Bundle data = getIntent().getExtras();
        mUserLogin = data.getString(Constants.User.USER_LOGIN);
        mUserName = data.getString(Constants.User.USER_NAME);
        
        final ScrollView sv = (ScrollView) findViewById(R.id.main_content);
        
        new LoadUserInfoTask(this).execute();
    }

    /**
     * An asynchronous task that runs on a background thread to load user info.
     */
    private static class LoadUserInfoTask extends AsyncTask<Void, Integer, User> {

        /** The target. */
        private WeakReference<UserActivity> mTarget;
        
        /** The exception. */
        private boolean mException;

        /**
         * Instantiates a new load user info task.
         *
         * @param activity the activity
         */
        public LoadUserInfoTask(UserActivity activity) {
            mTarget = new WeakReference<UserActivity>(activity);
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#doInBackground(Params[])
         */
        @Override
        protected User doInBackground(Void... arg0) {
            if (mTarget.get() != null) {
                try {
                    GitHubServiceFactory factory = GitHubServiceFactory.newInstance();
                    UserService userService = factory.createUserService();
                    
                    Authentication auth = new LoginPasswordAuthentication(mTarget.get().getAuthUsername(),
                            mTarget.get().getAuthPassword());
                    userService.setAuthentication(auth);
                    
                    return userService.getUserByUsername(mTarget.get().mUserLogin);
                }
                catch (GitHubException e) {
                    Log.e(Constants.LOG_TAG, e.getMessage(), e);
                    mException = true;
                    return null;
                }
            }
            else {
                return null;
            }
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
            if (mTarget.get() != null) {
                mTarget.get().mLoadingDialog = LoadingDialog.show(mTarget.get(), true, true);
            }
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(User result) {
            if (mTarget.get() != null) {
                mTarget.get().mLoadingDialog.dismiss();
                if (mException) {
                    mTarget.get().showError();
                }
                else {
                    mTarget.get().fillData(result);
                    new LoadWatchedReposTask(mTarget.get()).execute();
                    if (Constants.User.USER_TYPE_ORG.equals(result.getType())) { 
                        new LoadOrganizationMembersTask(mTarget.get()).execute();
                    }
                }
            }
        }
    }

    /**
     * An asynchronous task that runs on a background thread to load watched
     * repos.
     */
    private static class LoadWatchedReposTask extends AsyncTask<Void, Integer, Integer> {

        /** The target. */
        private WeakReference<UserActivity> mTarget;
        
        /** The exception. */
        private boolean mException;

        /**
         * Instantiates a new load watched repos task.
         *
         * @param activity the activity
         */
        public LoadWatchedReposTask(UserActivity activity) {
            mTarget = new WeakReference<UserActivity>(activity);
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#doInBackground(Params[])
         */
        @Override
        protected Integer doInBackground(Void... arg0) {
            if (mTarget.get() != null) {
                try {
                    GitHubServiceFactory factory = GitHubServiceFactory.newInstance();
                    UserService userService = factory.createUserService();
                    return userService.getWatchedRepositories(mTarget.get().mUserLogin).size();
                }
                catch (GitHubException e) {
                    Log.e(Constants.LOG_TAG, e.getMessage(), e);
                    mException = true;
                    return null;
                }
            }
            else {
                return null;
            }
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(Integer result) {
            if (mTarget.get() != null) {
                if (mException) {
                    //mTarget.get().showError();
                }
                else {
                    Button btnWatchedRepos = (Button) mTarget.get().findViewById(R.id.btn_watched_repos);
                    btnWatchedRepos.setText(String.valueOf(result));
                }
                ProgressBar progressBar = (ProgressBar) mTarget.get().findViewById(R.id.pb_watched_repos);
                progressBar.setVisibility(View.GONE);
            }
        }
    }
    
    private static class LoadOrganizationMembersTask extends AsyncTask<Void, Void, Integer> {

        /** The target. */
        private WeakReference<UserActivity> mTarget;
        
        /** The exception. */
        private boolean mException;

        /**
         * Instantiates a new load watched repos task.
         *
         * @param activity the activity
         */
        public LoadOrganizationMembersTask(UserActivity activity) {
            mTarget = new WeakReference<UserActivity>(activity);
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#doInBackground(Params[])
         */
        @Override
        protected Integer doInBackground(Void... arg0) {
            if (mTarget.get() != null) {
                try {
                    GitHubServiceFactory factory = GitHubServiceFactory.newInstance();
                    OrganizationService organizationService = factory.createOrganizationService();
                    return organizationService.getPublicMembers(mTarget.get().mUserLogin).size();
                }
                catch (GitHubException e) {
                    Log.e(Constants.LOG_TAG, e.getMessage(), e);
                    mException = true;
                    return null;
                }
            }
            return null;
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(Integer result) {
            if (mTarget.get() != null) {
                if (mException) {
                    //mTarget.get().showError();
                }
                else {
                    Button btnFollowers = (Button) mTarget.get().findViewById(R.id.btn_followers);
                    btnFollowers.setText(String.valueOf(result));
                }
                ProgressBar progressBar = (ProgressBar) mTarget.get().findViewById(R.id.pb_followers);
                progressBar.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Fill data into UI components.
     *
     * @param user the user
     */
    protected void fillData(User user) {
        mUser = user;
        ImageView ivGravatar = (ImageView) findViewById(R.id.iv_gravatar);
        ImageDownloader.getInstance().download(user.getGravatarId(), ivGravatar, 80);

        TextView tvName = (TextView) findViewById(R.id.tv_name);
        TextView tvCreated = (TextView) findViewById(R.id.tv_created_at);
        LinearLayout llEmail = (LinearLayout) findViewById(R.id.ll_user_email);
        LinearLayout llWebsite = (LinearLayout) findViewById(R.id.ll_user_website);
        LinearLayout llLocation = (LinearLayout) findViewById(R.id.ll_user_location);
        LinearLayout llCompany = (LinearLayout) findViewById(R.id.ll_user_company);

        Button btnNews = (Button) findViewById(R.id.btn_news);
        btnNews.setOnClickListener(this);
        if (mUserLogin.equals(getAuthUsername())) {
            btnNews.setOnClickListener(this);
            btnNews.setVisibility(View.VISIBLE);
        }
        else {
            btnNews.setVisibility(View.GONE);
        }
        
        Button btnPublicActivity = (Button) findViewById(R.id.btn_public_activity);
        btnPublicActivity.setOnClickListener(this);
        
<<<<<<< HEAD
        Button btnYourActions = (Button) findViewById(R.id.btn_your_actions);
        
        if (mUserLogin.equals(getAuthUsername())) {
            btnYourActions.setOnClickListener(this);
            btnYourActions.setVisibility(View.VISIBLE);
        }
        else {
            btnYourActions.setVisibility(View.GONE);
        }
=======
//        Button btnYourActions = (Button) findViewById(R.id.btn_your_actions);
//        
//        if (mUserLogin.equals(getAuthUsername())) {
//            btnYourActions.setOnClickListener(this);
//            btnYourActions.setVisibility(View.VISIBLE);
//        }
//        else {
//            btnYourActions.setVisibility(View.GONE);
//        }
>>>>>>> upstream/master

        Button btnPublicRepos = (Button) findViewById(R.id.btn_pub_repos);
        btnPublicRepos.setOnClickListener(this);

        Button btnWatchedRepos = (Button) findViewById(R.id.btn_watched_repos);
        btnWatchedRepos.setOnClickListener(this);

        Button btnFollowers = (Button) findViewById(R.id.btn_followers);
        btnFollowers.setOnClickListener(this);
        TextView tvFollowers = (TextView) findViewById(R.id.tv_followers_label);
        if (Constants.User.USER_TYPE_USER.equals(user.getType())) {
            tvFollowers.setText(R.string.user_followers);
        }
        else {
            tvFollowers.setText(R.string.user_members);
        }
        
        //hide following if organization
        RelativeLayout rlFollowing = (RelativeLayout) findViewById(R.id.rl_following);
        if (Constants.User.USER_TYPE_USER.equals(user.getType())) {
            Button btnFollowing = (Button) findViewById(R.id.btn_following);
            btnFollowing.setText(String.valueOf(user.getFollowingCount()));
            btnFollowing.setOnClickListener(this);
            rlFollowing.setVisibility(View.VISIBLE);
        }
        else {
            rlFollowing.setVisibility(View.GONE);
        }
        
        //hide organizations if organization
        RelativeLayout rlOrganizations = (RelativeLayout) findViewById(R.id.rl_organizations);
        if (Constants.User.USER_TYPE_USER.equals(user.getType())) {
            ImageButton btnOrganizations = (ImageButton) findViewById(R.id.btn_organizations);
            btnOrganizations.setOnClickListener(this);
            registerForContextMenu(btnOrganizations);
            rlOrganizations.setVisibility(View.VISIBLE);
        }
        else {
            rlOrganizations.setVisibility(View.GONE);
        }

        tvName.setText(StringUtils.formatName(user.getLogin(), user.getName()));
        if (Constants.User.USER_TYPE_ORG.equals(user.getType())) {
            tvName.append(" (");
            tvName.append(Constants.User.USER_TYPE_ORG);
            tvName.append(")");
        }
        tvCreated.setText(user.getCreatedAt() != null ? 
                getResources().getString(R.string.user_created_at,
                        StringUtils.formatDate(user.getCreatedAt())) : "");

        //show email row if not blank
        if (!StringUtils.isBlank(user.getEmail())) {
            TextView tvEmail = (TextView) findViewById(R.id.tv_email);
            tvEmail.setText(user.getEmail());
            llEmail.setVisibility(View.VISIBLE);
        }
        else {
            llEmail.setVisibility(View.GONE);
        }
        
        //show website if not blank
        if (!StringUtils.isBlank(user.getBlog())) {
            TextView tvWebsite = (TextView) findViewById(R.id.tv_website);
            tvWebsite.setText(user.getBlog());
            llWebsite.setVisibility(View.VISIBLE);
        }
        else {
            llWebsite.setVisibility(View.GONE);
        }
        
        if (!StringUtils.isBlank(user.getCompany())) {
            TextView tvCompany = (TextView) findViewById(R.id.tv_company);
            tvCompany.setText(user.getCompany());
            llCompany.setVisibility(View.VISIBLE);
        }
        else {
            llCompany.setVisibility(View.GONE);
        }
        
        //Show location if not blank
        if (!StringUtils.isBlank(user.getLocation())) {
            TextView tvLocation = (TextView) findViewById(R.id.tv_location);
            tvLocation.setText(user.getLocation());
            llLocation.setVisibility(View.VISIBLE);
        }
        else {
            llLocation.setVisibility(View.GONE);
        }
        
        btnPublicRepos.setText(String.valueOf(user.getPublicRepoCount() + user.getTotalPrivateRepoCount()));
        
        if (Constants.User.USER_TYPE_USER.equals(user.getType())) {
            btnFollowers.setText(String.valueOf(user.getFollowersCount()));
            ProgressBar progressBar = (ProgressBar) findViewById(R.id.pb_followers);
            progressBar.setVisibility(View.GONE);
        }
    }

    /*
     * (non-Javadoc)
     * @see android.view.View.OnClickListener#onClick(android.view.View)
     */
    public void onClick(View view) {
        int id = view.getId();

        switch (id) {
        case R.id.btn_news:
            getFeeds(view);
            break;
        case R.id.btn_public_activity:
            getPublicActivities(view);
            break;
        case R.id.btn_your_actions:
            getYourActions(view);
            break;
        case R.id.btn_pub_repos:
            getPublicRepos(view);
            break;
        case R.id.btn_followers:
            getFollowers(view);
            break;
        case R.id.btn_following:
            getFollowing(view);
            break;
        case R.id.btn_watched_repos:
            getWatchedRepos(view);
            break;
        case R.id.btn_organizations:
            view.showContextMenu();
            break;
        default:
            break;
        }
    }

    /**
     * Gets the feeds when Activity button clicked.
     *
     * @param view the view
     * @return the feeds
     */
    public void getFeeds(View view) {
        Intent intent = new Intent().setClass(this, UserPrivateActivity.class);
        intent.putExtra(Constants.User.USER_LOGIN, mUserLogin);
        intent.putExtra(Constants.ACTIONBAR_TITLE, mUserLogin
                + (!StringUtils.isBlank(mUserName) ? " - " + mUserName : ""));
        intent.putExtra(Constants.SUBTITLE, getResources().getString(R.string.user_news_feed));
        startActivity(intent);
    }

    /**
     * Gets the your actions.
     *
     * @param view the view
     * @return the your actions
     */
    public void getPublicActivities(View view) {
        Intent intent = new Intent().setClass(this, UserPublicActivity.class);
        intent.putExtra(Constants.User.USER_LOGIN, mUserLogin);
        intent.putExtra(Constants.ACTIONBAR_TITLE, mUserLogin);
        intent.putExtra(Constants.SUBTITLE, getResources().getString(R.string.user_public_activity));
        startActivity(intent);
    }
    
    public void getYourActions(View view) {
        Intent intent = new Intent().setClass(this, UserYourActionsActivity.class);
        intent.putExtra(Constants.User.USER_LOGIN, mUserLogin);
        startActivity(intent);
    }
    
    /**
     * Gets the public repos when Public Repository button clicked.
     *
     * @param view the view
     * @return the public repos
     */
    public void getPublicRepos(View view) {
        Intent intent = new Intent().setClass(this, PublicRepoListActivity.class);
        intent.putExtra(Constants.Repository.REPO_OWNER, mUserLogin);
        intent.putExtra(Constants.User.USER_NAME, mUserName);
        startActivity(intent);
    }

    /**
     * Gets the followers when Followers button clicked.
     *
     * @param view the view
     * @return the followers
     */
    public void getFollowers(View view) {
        if (Constants.User.USER_TYPE_ORG.equals(mUser.getType())) {
            Intent intent = new Intent().setClass(this, OrganizationMemberListActivity.class);
            intent.putExtra(Constants.Repository.REPO_OWNER, mUserLogin);
            startActivity(intent);
        }
        else {
            Intent intent = new Intent().setClass(this, FollowerFollowingListActivity.class);
            intent.putExtra(Constants.User.USER_LOGIN, mUserLogin);
            if (Constants.User.USER_TYPE_USER.equals(mUser.getType())) {
                intent.putExtra(Constants.SUBTITLE, getResources().getString(R.string.user_followers));
            }
            else {
                intent.putExtra(Constants.SUBTITLE, getResources().getString(R.string.user_members));
            }
            intent.putExtra(Constants.FIND_FOLLOWER, true);
            startActivity(intent);
        }
    }

    /**
     * Gets the following when Following button clicked.
     *
     * @param view the view
     * @return the following
     */
    public void getFollowing(View view) {
        Intent intent = new Intent().setClass(this, FollowerFollowingListActivity.class);
        intent.putExtra(Constants.User.USER_LOGIN, mUserLogin);
        intent.putExtra(Constants.ACTIONBAR_TITLE, mUserLogin
                + (!StringUtils.isBlank(mUserName) ? " - " + mUserName : ""));
        intent.putExtra(Constants.SUBTITLE, getResources().getString(R.string.user_following));
        intent.putExtra(Constants.FIND_FOLLOWER, false);
        startActivity(intent);
    }

    /**
     * Gets the watched repos.
     * 
     * @param view the view
     * @return the watched repos
     */
    public void getWatchedRepos(View view) {
        Intent intent = new Intent().setClass(this, WatchedRepoListActivity.class);
        intent.putExtra(Constants.Repository.REPO_OWNER, mUserLogin);
        intent.putExtra(Constants.User.USER_NAME, mUserName);
        startActivity(intent);
    }
    
    /* (non-Javadoc)
     * @see android.widget.AdapterView.OnItemClickListener#onItemClick(android.widget.AdapterView, android.view.View, int, long)
     */
    @Override
    public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        // TODO Auto-generated method stub
        
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        if (v.getId() == R.id.btn_organizations) {
            menu.setHeaderTitle("Choose Organization");
            GitHubServiceFactory factory = GitHubServiceFactory.newInstance();
            UserService userService = factory.createUserService();
            try {
                Authentication auth = new LoginPasswordAuthentication(getAuthUsername(), getAuthPassword());
                userService.setAuthentication(auth);
                List<Organization> organizations = userService.getUserOrganizations(mUserLogin);
                OrganizationService a;
                if (organizations != null && !organizations.isEmpty()) {
                    for (Organization organization : organizations) {
                        menu.add(organization.getLogin());      
                    }
                }
                else {
                    getApplicationContext().notFoundMessage(this, "Organizations");
                }
            } catch (GitHubException e) {
                Log.e(Constants.LOG_TAG, e.getMessage(), e);
                showError();
            }
        }
    }
    
    public boolean onContextItemSelected(MenuItem item) {
        String orgLogin = item.getTitle().toString();
        getApplicationContext().openUserInfoActivity(this, orgLogin, null);
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isAuthenticated()) {
            if (!mUserLogin.equals(getAuthUsername())) {
                if (menu.size() == 1) {
                    MenuInflater inflater = getMenuInflater();
                    inflater.inflate(R.menu.user_menu, menu);
                    
                }
            }
        }
        if (menu.size() == 1) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.about_menu, menu);
        }
        return true;
    }
    
    @Override
    public boolean setMenuOptionItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.follow_action:
                new FollowUnfollowTask(this).execute(true);
                return true;
            case R.id.unfollow_action:
                new FollowUnfollowTask(this).execute(false);
                return true;
            case R.id.about:
                openAboutDialog();
                return true;
            case R.id.feedback:
                openFeedbackDialog();
                return true;
            default:
                return true;
        }
    }
    
    private void openAboutDialog() {
        Dialog dialog = new Dialog(this);

        dialog.setContentView(R.layout.about_dialog);
        
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = packageInfo.versionName;
            dialog.setTitle(getResources().getString(R.string.app_name) + " V" + versionName);
        } 
        catch (PackageManager.NameNotFoundException e) {
            dialog.setTitle(getResources().getString(R.string.app_name));
        }
        
        dialog.show();
    }
    
    private void openFeedbackDialog() {
        Dialog dialog = new Dialog(this);

        dialog.setContentView(R.layout.feedback_dialog);
        dialog.setTitle(getResources().getString(R.string.feedback));
        
        Button btnByEmail = (Button) dialog.findViewById(R.id.btn_by_email);
        btnByEmail.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View arg0) {
                Intent sendIntent = new Intent(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{getResources().getString(R.string.my_email)});
                sendIntent.setType("message/rfc822");
                startActivity(Intent.createChooser(sendIntent, "Select email application."));
            }
        });
        
        Button btnByGh4a = (Button) dialog.findViewById(R.id.btn_by_gh4a);
        btnByGh4a.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View arg0) {
                if (isAuthenticated()) {
                    Intent intent = new Intent().setClass(UserActivity.this, IssueCreateActivity.class);
                    intent.putExtra(Constants.Repository.REPO_OWNER, getResources().getString(R.string.my_username));
                    intent.putExtra(Constants.Repository.REPO_NAME, getResources().getString(R.string.my_repo));
                    startActivity(intent);
                }
                else {
                    showMessage("Please login", false);
                }
            }
        });
        
        dialog.show();
    }
    
    private static class FollowUnfollowTask extends AsyncTask<Boolean, Void, Boolean> {

        /** The target. */
        private WeakReference<UserActivity> mTarget;
        
        /** The exception. */
        private boolean mException;
        
        private boolean isFollowAction;

        /**
         * Instantiates a new load watched repos task.
         *
         * @param activity the activity
         */
        public FollowUnfollowTask(UserActivity activity) {
            mTarget = new WeakReference<UserActivity>(activity);
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#doInBackground(Params[])
         */
        @Override
        protected Boolean doInBackground(Boolean... arg0) {
            if (mTarget.get() != null) {
                isFollowAction = arg0[0];
                try {
                    GitHubServiceFactory factory = GitHubServiceFactory.newInstance();
                    UserService userService = factory.createUserService();
                    Authentication auth = new LoginPasswordAuthentication(mTarget.get().getAuthUsername(),
                            mTarget.get().getAuthPassword());
                    userService.setAuthentication(auth);
                    if (isFollowAction) {
                        userService.followUser(mTarget.get().mUserLogin);
                    }
                    else {
                        userService.unfollowUser(mTarget.get().mUserLogin);
                    }
                    return true;
                }
                catch (GitHubException e) {
                    Log.e(Constants.LOG_TAG, e.getMessage(), e);
                    mException = true;
                    return null;
                }
            }
            else {
                return null;
            }
        }

        @Override
        protected void onPreExecute() {
            if (mTarget.get() != null) {
                mTarget.get().mLoadingDialog = LoadingDialog.show(mTarget.get(), true, true, false);
            }
        }
        
        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(Boolean result) {
            if (mTarget.get() != null) {
                mTarget.get().mLoadingDialog.dismiss();
                if (mException) {
                    if (isFollowAction) {
                        mTarget.get().showMessage(mTarget.get().getResources().getString(R.string.user_error_follow,
                                mTarget.get().mUserLogin), false);
                    }
                    else {
                        mTarget.get().showMessage(mTarget.get().getResources().getString(R.string.user_error_unfollow,
                                mTarget.get().mUserLogin), false);
                    }
                }
                else {
                    if (isFollowAction) {
                        mTarget.get().showMessage(mTarget.get().getResources().getString(R.string.user_success_follow,
                                mTarget.get().mUserLogin), false);
                    }
                    else {
                        mTarget.get().showMessage(mTarget.get().getResources().getString(R.string.user_success_unfollow,
                                mTarget.get().mUserLogin), false);
                    }
                }
            }
        }
    }
}