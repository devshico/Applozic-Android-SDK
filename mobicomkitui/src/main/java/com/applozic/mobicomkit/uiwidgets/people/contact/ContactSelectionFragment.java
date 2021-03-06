package com.applozic.mobicomkit.uiwidgets.people.contact;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.support.v7.widget.AppCompatCheckBox;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AlphabetIndexer;
import android.widget.Button;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;

import com.applozic.mobicomkit.ApplozicClient;
import com.applozic.mobicomkit.api.account.user.MobiComUserPreference;
import com.applozic.mobicomkit.api.account.user.RegisteredUsersAsyncTask;
import com.applozic.mobicomkit.channel.service.ChannelService;
import com.applozic.mobicomkit.contact.AppContactService;
import com.applozic.mobicomkit.contact.database.ContactDatabase;
import com.applozic.mobicomkit.feed.RegisteredUsersApiResponse;
import com.applozic.mobicomkit.uiwidgets.ApplozicSetting;
import com.applozic.mobicomkit.uiwidgets.R;
import com.applozic.mobicomkit.uiwidgets.alphanumbericcolor.AlphaNumberColorUtil;
import com.applozic.mobicomkit.uiwidgets.async.ApplozicChannelCreateTask;
import com.applozic.mobicomkit.uiwidgets.conversation.ConversationUIService;
import com.applozic.mobicomkit.uiwidgets.conversation.activity.ChannelCreateActivity;
import com.applozic.mobicomkit.uiwidgets.conversation.activity.ChannelInfoActivity;
import com.applozic.mobicomkit.uiwidgets.conversation.activity.ContactSelectionActivity;
import com.applozic.mobicomkit.uiwidgets.conversation.activity.ConversationActivity;
import com.applozic.mobicommons.commons.core.utils.Utils;
import com.applozic.mobicommons.commons.image.ImageLoader;
import com.applozic.mobicommons.people.SearchListFragment;
import com.applozic.mobicommons.people.channel.Channel;
import com.applozic.mobicommons.people.contact.Contact;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Created by sunil on 28/9/16.
 */
public class ContactSelectionFragment extends ListFragment implements SearchListFragment,
        AdapterView.OnItemClickListener, LoaderManager.LoaderCallbacks<Cursor> {

    public static final String CHANNEL = "CHANNEL_NAME";
    public static final String CHANNEL_OBJECT = "CHANNEL";
    public static final String CHECK_BOX = "CHECK_BOX";
    public static final String IMAGE_LINK = "IMAGE_LINK";
    private static final String STATE_PREVIOUSLY_SELECTED_KEY =
            "SELECTED_ITEM";
    public static boolean isSearching = false;
    ApplozicSetting applozicSetting;
    ContactDatabase contactDatabase;
    boolean disableCheckBox;
    boolean isUserPresnt;
    AppContactService appContactService;
    Channel channel;
    MobiComUserPreference userPreference;
    private String mSearchTerm; // Stores the current search query term
    private ContactsAdapter mAdapter;
    private boolean isScrolling = false;
    private int visibleThreshold = 0;
    private int currentPage = 0;
    private int previousTotalItemCount = 0;
    private boolean loading = true;
    private int startingPageIndex = 0;
    private ImageLoader mImageLoader;
    private int mPreviouslySelectedSearchItem = 0;
    private String imageUrl;
    private String channelName;
    private Bundle bundle;
    private List<String> userIdList;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bundle = getArguments();
        if (bundle != null) {
            channel = (Channel) bundle.getSerializable(CHANNEL_OBJECT);
            disableCheckBox = bundle.getBoolean(CHECK_BOX, false);
            channelName = bundle.getString(CHANNEL);
            imageUrl = bundle.getString(IMAGE_LINK);
        }
        userPreference = MobiComUserPreference.getInstance(getActivity());
        setHasOptionsMenu(true);

        if (savedInstanceState != null) {
            mSearchTerm = savedInstanceState.getString(SearchManager.QUERY);
            mPreviouslySelectedSearchItem =
                    savedInstanceState.getInt(STATE_PREVIOUSLY_SELECTED_KEY, 0);
        }

        contactDatabase = new ContactDatabase(getContext());
        appContactService = new AppContactService(getActivity());
        mAdapter = new ContactsAdapter(getActivity());
        applozicSetting = ApplozicSetting.getInstance(getContext());
        final Context context = getActivity().getApplicationContext();
        mImageLoader = new ImageLoader(context, getListPreferredItemHeight()) {
            @Override
            protected Bitmap processBitmap(Object data) {
                return appContactService.downloadContactImage(context, (Contact) data);
            }
        };
        // Set a placeholder loading image for the image loader
        mImageLoader.setLoadingImage(R.drawable.applozic_ic_contact_picture_holo_light);
        // Add a cache to the image loader
        mImageLoader.addImageCache(getActivity().getSupportFragmentManager(), 0.1f);
        mImageLoader.setImageFadeIn(false);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {

        final Cursor cursor = mAdapter.getCursor();
        cursor.moveToPosition(position);
        Contact contact = contactDatabase.getContact(cursor, "_id");
        if (disableCheckBox) {
            isUserPresnt = ChannelService.getInstance(getActivity()).isUserAlreadyPresentInChannel(channel.getKey(), contact.getContactIds());
            if (!isUserPresnt) {
                Intent intent = new Intent();
                intent.putExtra(ChannelInfoActivity.USERID, contact.getUserId());
                getActivity().setResult(getActivity().RESULT_OK, intent);
                getActivity().finish();
            }
        }
    }

    @SuppressLint("NewApi")
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setListAdapter(mAdapter);
        getListView().setOnItemClickListener(this);
        getListView().setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int scrollState) {
                // Pause image loader to ensure smoother scrolling when flinging
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING) {
                    mImageLoader.setPauseWork(true);
                    Utils.toggleSoftKeyBoard(getActivity(), true);
                } else {
                    mImageLoader.setPauseWork(false);
                }
            }

            @Override
            public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemsCount) {
                if (ApplozicSetting.getInstance(getContext()).isRegisteredUsersContactCall() && Utils.isInternetAvailable(getActivity().getApplicationContext())) {

                    if (totalItemsCount < previousTotalItemCount) {
                        currentPage = startingPageIndex;
                        previousTotalItemCount = totalItemsCount;
                        if (totalItemsCount == 0) {
                            loading = true;
                        }
                    }

                    if (loading && (totalItemsCount > previousTotalItemCount)) {
                        loading = false;
                        previousTotalItemCount = totalItemsCount;
                        currentPage++;
                    }

                    if (!loading && (totalItemsCount - visibleItemCount) <= (firstVisibleItem + visibleThreshold)) {
                        if (!ContactSelectionActivity.isSearching) {
                            loading = true;
                            processDownloadRegisteredUsers();
                        }
                    }
                }
            }
        });

        // If there's a previously selected search item from a saved state then don't bother
        // initializing the loader as it will be restarted later when the query is populated into
        // the action bar search view (see onQueryTextChange() in onCreateOptionsMenu()).
        if (mPreviouslySelectedSearchItem == 0) {
            // Initialize the loader, and create a loader identified by ContactsQuery.QUERY_ID
            getLoaderManager().initLoader(ContactsQuery.QUERY_ID, null, this);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // In the case onPause() is called during a fling the image loader is
        // un-paused to let any remaining background work complete.
        mImageLoader.setPauseWork(false);
    }

    public void processDownloadRegisteredUsers() {

        final ProgressDialog progressDialog = ProgressDialog.show(getActivity(), "",
                getActivity().getString(R.string.applozic_contacts_loading_info), true);

        RegisteredUsersAsyncTask.TaskListener usersAsyncTaskTaskListener = new RegisteredUsersAsyncTask.TaskListener() {
            @Override
            public void onSuccess(RegisteredUsersApiResponse registeredUsersApiResponse, String[] userIdArray) {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                if (registeredUsersApiResponse != null) {
                    try {
                        if (!Utils.isInternetAvailable(getActivity())) {
                            Toast toast = Toast.makeText(getActivity(), getActivity().getString(R.string.applozic_contacts_loading_error), Toast.LENGTH_SHORT);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                        }
                        if (registeredUsersApiResponse != null) {
                            getLoaderManager().restartLoader(
                                    ContactSelectionFragment.ContactsQuery.QUERY_ID, null, ContactSelectionFragment.this);
                        }

                    } catch (Exception e) {

                    }
                }

            }

            @Override
            public void onFailure(RegisteredUsersApiResponse registeredUsersApiResponse, String[] userIdArray, Exception exception) {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                String error = getString(Utils.isInternetAvailable(getActivity()) ? R.string.applozic_server_error : R.string.you_need_network_access_for_block_or_unblock);
                Toast toast = Toast.makeText(getActivity(), error, Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
            }

            @Override
            public void onCompletion() {

            }
        };
        RegisteredUsersAsyncTask usersAsyncTask = new RegisteredUsersAsyncTask(getActivity(), usersAsyncTaskTaskListener, applozicSetting.getTotalRegisteredUsers(), userPreference.getRegisteredUsersLastFetchTime(), null, null, true);
        usersAsyncTask.execute((Void) null);

    }

    private int getListPreferredItemHeight() {
        final TypedValue typedValue = new TypedValue();

        // Resolve list item preferred height theme attribute into typedValue
        getActivity().getTheme().resolveAttribute(
                android.R.attr.listPreferredItemHeight, typedValue, true);

        // Create a new DisplayMetrics object
        final DisplayMetrics metrics = new DisplayMetrics();

        // Populate the DisplayMetrics
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);

        // Return theme value based on DisplayMetrics
        return (int) typedValue.getDimension(metrics);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the list fragment layout
        View view = inflater.inflate(R.layout.contact_list_fragment, container, false);
        Button shareButton = (Button) view.findViewById(R.id.actionButton);
        shareButton.setVisibility(ApplozicSetting.getInstance(getActivity()).isInviteFriendsButtonVisible() ? View.VISIBLE : View.GONE);
        TextView resultTextView = (TextView) view.findViewById(R.id.result);
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.Done) {
            if (userIdList != null && userIdList.size() == 0) {
                Toast.makeText(getActivity(), R.string.select_at_least, Toast.LENGTH_SHORT).show();
            } else {
                final ProgressDialog progressDialog = ProgressDialog.show(getActivity(), "",
                        getActivity().getString(R.string.group_creating_info), true);
                ApplozicChannelCreateTask.ChannelCreateListener channelCreateListener = new ApplozicChannelCreateTask.ChannelCreateListener() {
                    @Override
                    public void onSuccess(Channel channel, Context context) {
                        if (progressDialog != null && progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        if (channel != null) {
                            Intent intent = new Intent(getActivity(), ConversationActivity.class);
                            if (ApplozicClient.getInstance(getActivity().getApplicationContext()).isContextBasedChat()) {
                                intent.putExtra(ConversationUIService.CONTEXT_BASED_CHAT, true);
                            }
                            intent.putExtra(ConversationUIService.GROUP_ID, channel.getKey());
                            intent.putExtra(ConversationUIService.GROUP_NAME, channel.getName());
                            getActivity().startActivity(intent);
                        }

                        if (bundle != null && bundle.getString(CHANNEL) != null) {
                            getActivity().sendBroadcast(new Intent(ChannelCreateActivity.ACTION_FINISH_CHANNEL_CREATE));
                        }
                        if (getActivity() != null) {
                            getActivity().finish();
                        }
                    }

                    @Override
                    public void onFailure(Exception e, Context context) {
                        if (progressDialog != null && progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }

                    }
                };

                if (!TextUtils.isEmpty(channelName) && userIdList != null && userIdList.size() > 0) {
                    ApplozicChannelCreateTask applozicChannelCreateTask = new ApplozicChannelCreateTask(getActivity(), channelCreateListener, channelName, userIdList, imageUrl);
                    applozicChannelCreateTask.execute((Void) null);
                }

            }
            return true;
        }
        return false;

    }


    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        Loader<Cursor> loader = contactDatabase.getSearchCursorLoader(mSearchTerm, null);
        return loader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // This swaps the new cursor into the adapter.
        if (loader.getId() == ContactsQuery.QUERY_ID) {
            mAdapter.swapCursor(data);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

        if (loader.getId() == ContactsQuery.QUERY_ID) {
            // When the loader is being reset, clear the cursor from the adapter. This allows the
            // cursor resources to be freed.
            mAdapter.swapCursor(null);
        }
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        // Called when the action bar search text has changed.  Updates
        // the search filter, and restarts the loader to do a new query
        // using the new search string.
        String newFilter = !TextUtils.isEmpty(newText) ? newText : null;

        // Don't do anything if the filter is empty

        // Updates current filter to new filter
        mSearchTerm = newFilter;
        mAdapter.indexOfSearchQuery(newFilter);

        getLoaderManager().restartLoader(
                ContactSelectionFragment.ContactsQuery.QUERY_ID, null, ContactSelectionFragment.this);

        return true;
    }


    /**
     * This interface defines constants for the Cursor and CursorLoader, based on constants defined
     * in the {@link android.provider.ContactsContract.Contacts} class.
     */
    public interface ContactsQuery {
        // An identifier for the loader
        int QUERY_ID = 1;

    }

    private class ContactsAdapter extends CursorAdapter implements SectionIndexer {


        private Context context;
        private LayoutInflater mInflater; // Stores the layout inflater
        private AlphabetIndexer mAlphabetIndexer; // Stores the AlphabetIndexer instance
        private TextAppearanceSpan highlightTextSpan; // Stores the highlight text appearance style

        /**
         * Instantiates a new Contacts Adapter.
         *
         * @param context A context that has access to the app's layout.
         */
        public ContactsAdapter(Context context) {
            super(context, null, 0);
            this.context = context;
            userIdList = new ArrayList<String>();
            // Stores inflater for use later
            mInflater = LayoutInflater.from(context);
            // Loads a string containing the English alphabet. To fully localize the app, provide a
            // strings.xml file in res/values-<x> directories, where <x> is a locale. In the file,
            // define a string with android:name="alphabet" and contents set to all of the
            // alphabetic characters in the language in their proper sort order, in upper case if
            // applicable.
            final String alphabet = context.getString(R.string.alphabet);

            // Instantiates a new AlphabetIndexer bound to the column used to sort contact names.
            // The cursor is left null, because it has not yet been retrieved.
            mAlphabetIndexer = new AlphabetIndexer(null, 1, alphabet);

            // Defines a span for highlighting the part of a display name that matches the search
            // string
            highlightTextSpan = new TextAppearanceSpan(context, R.style.searchTextHiglight);
        }

        /**
         * Identifies the start of the search string in the display name column of a Cursor row.
         * E.g. If displayName was "Adam" and search query (mSearchTerm) was "da" this would
         * return 1.
         *
         * @param displayName The contact display name.
         * @return The starting position of the search string in the display name, 0-based. The
         * method returns -1 if the string is not found in the display name, or if the search
         * string is empty or null.
         */
        private int indexOfSearchQuery(String displayName) {
            if (!TextUtils.isEmpty(mSearchTerm)) {
                return displayName.toLowerCase(Locale.getDefault()).indexOf(
                        mSearchTerm.toLowerCase(Locale.getDefault()));
            }
            return -1;
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            final View itemLayout =
                    mInflater.inflate(R.layout.contact_select_list_item, parent, false);

            final ContactViewHolder holder = new ContactViewHolder();

            holder.textView1 = (TextView) itemLayout.findViewById(R.id.applozic_group_member_info);
            holder.textView2 = (TextView) itemLayout.findViewById(R.id.displayName);
            holder.contactNumberTextView = (TextView) itemLayout.findViewById(R.id.contactNumberTextView);
            holder.checkBox = (AppCompatCheckBox) itemLayout.findViewById(R.id.checkbox);
            holder.checkBox.setVisibility(View.VISIBLE);
            holder.alphabeticImage = (TextView) itemLayout.findViewById(R.id.alphabeticImage);
            holder.circleImageView = (CircleImageView) itemLayout.findViewById(R.id.contactImage);
            itemLayout.setTag(holder);
            return itemLayout;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {

            final ContactViewHolder holder = (ContactViewHolder) view.getTag();
            String contactNumber;
            char firstLetter = 0;

            final Contact contact = contactDatabase.getContact(cursor, "_id");

            if (disableCheckBox) {
                isUserPresnt = ChannelService.getInstance(context).isUserAlreadyPresentInChannel(channel.getKey(), contact.getContactIds());
                if (isUserPresnt) {
                    holder.textView1.setVisibility(View.VISIBLE);
                    holder.contactNumberTextView.setVisibility(View.GONE);
                    holder.textView1.setTextColor(ContextCompat.getColor(context, R.color.applozic_lite_black_color));
                    holder.textView2.setTextColor(ContextCompat.getColor(context, R.color.applozic_lite_black_color));
                } else {
                    holder.textView1.setVisibility(View.GONE);
                    holder.contactNumberTextView.setVisibility(View.VISIBLE);
                    holder.textView2.setTextColor(ContextCompat.getColor(context, R.color.black));
                }
                holder.checkBox.setVisibility(View.GONE);
            } else {
                holder.checkBox.setVisibility(View.VISIBLE);
                holder.contactNumberTextView.setVisibility(View.VISIBLE);
                holder.textView2.setTextColor(ContextCompat.getColor(context, R.color.black));
            }

            if (contact != null && !TextUtils.isEmpty(contact.getDisplayName())) {
                contactNumber = contact.getDisplayName().toUpperCase();
                firstLetter = contact.getDisplayName().toUpperCase().charAt(0);
                if (firstLetter != '+') {
                    holder.alphabeticImage.setText(String.valueOf(firstLetter));
                } else if (contactNumber.length() >= 2) {
                    holder.alphabeticImage.setText(String.valueOf(contactNumber.charAt(1)));
                }
                Character colorKey = AlphaNumberColorUtil.alphabetBackgroundColorMap.containsKey(firstLetter) ? firstLetter : null;
                GradientDrawable bgShape = (GradientDrawable) holder.alphabeticImage.getBackground();
                bgShape.setColor(context.getResources().getColor(AlphaNumberColorUtil.alphabetBackgroundColorMap.get(colorKey)));
            }
            holder.alphabeticImage.setVisibility(View.GONE);
            holder.circleImageView.setVisibility(View.VISIBLE);
            if (contact != null) {
                if (contact.isDrawableResources()) {
                    int drawableResourceId = context.getResources().getIdentifier(contact.getrDrawableName(), "drawable", context.getPackageName());
                    holder.circleImageView.setImageResource(drawableResourceId);
                } else {
                    mImageLoader.loadImage(contact, holder.circleImageView, holder.alphabeticImage);
                }
            }
            holder.textView2.setText(contact.getDisplayName());
            if (!TextUtils.isEmpty(contact.getContactNumber())) {
                holder.contactNumberTextView.setText(contact.getContactNumber());
            } else {
                holder.contactNumberTextView.setText("");
            }
            holder.checkBox.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    AppCompatCheckBox checkBox = (AppCompatCheckBox) v;
                    if (checkBox.isChecked()) {
                        userIdList.add(contact.getContactIds());
                    } else if (!checkBox.isChecked()) {
                        userIdList.remove(contact.getContactIds());
                    }
                }
            });

            // If the selected items contains the current item, set the checkbox to be checked

            holder.checkBox.setChecked(userIdList.contains(contact.getContactIds()));


            ///////////////////////
            final int startIndex = indexOfSearchQuery(contact.getDisplayName());

            if (startIndex == -1) {
                // If the user didn't do a search, or the search string didn't match a display
                // name, show the display name without highlighting
                holder.textView2.setText(contact.getDisplayName());
            } else {
                // If the search string matched the display name, applies a SpannableString to
                // highlight the search string with the displayed display name

                // Wraps the display name in the SpannableString
                final SpannableString highlightedName = new SpannableString(contact.getDisplayName());

                // Sets the span to start at the starting point of the match and end at "length"
                // characters beyond the starting point
                highlightedName.setSpan(highlightTextSpan, startIndex,
                        startIndex + mSearchTerm.length(), 0);

                // Binds the SpannableString to the display name View object
                holder.textView2.setText(highlightedName);

            }

        }

        /**
         * Overrides swapCursor to move the new Cursor into the AlphabetIndex as well as the
         * CursorAdapter.
         */
        @Override
        public Cursor swapCursor(Cursor newCursor) {
            // Update the AlphabetIndexer with new cursor as well
            mAlphabetIndexer.setCursor(newCursor);
            return super.swapCursor(newCursor);
        }

        /**
         * An override of getCount that simplifies accessing the Cursor. If the Cursor is null,
         * getCount returns zero. As a result, no test for Cursor == null is needed.
         */
        @Override
        public int getCount() {
            if (getCursor() == null) {
                return 0;
            }
            return super.getCount();
        }

        /**
         * Defines the SectionIndexer.getSections() interface.
         */
        @Override
        public Object[] getSections() {
            return mAlphabetIndexer.getSections();
        }

        /**
         * Defines the SectionIndexer.getPositionForSection() interface.
         */
        @Override
        public int getPositionForSection(int i) {
            if (getCursor() == null) {
                return 0;
            }
            return mAlphabetIndexer.getPositionForSection(i);
        }

        /**
         * Defines the SectionIndexer.getSectionForPosition() interface.
         */
        @Override
        public int getSectionForPosition(int i) {
            if (getCursor() == null) {
                return 0;
            }
            return mAlphabetIndexer.getSectionForPosition(i);
        }

    }

    private class ContactViewHolder {
        AppCompatCheckBox checkBox;
        TextView textView1, contactNumberTextView;
        TextView alphabeticImage;
        CircleImageView circleImageView;
        TextView textView2;
    }

}
