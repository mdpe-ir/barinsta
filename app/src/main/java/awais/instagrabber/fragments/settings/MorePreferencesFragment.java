package awais.instagrabber.fragments.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;

import java.util.List;

import awais.instagrabber.BuildConfig;
import awais.instagrabber.R;
import awais.instagrabber.activities.Login;
import awais.instagrabber.databinding.PrefAccountSwitcherBinding;
import awais.instagrabber.db.datasources.AccountDataSource;
import awais.instagrabber.db.entities.Account;
import awais.instagrabber.db.repositories.AccountRepository;
import awais.instagrabber.db.repositories.RepositoryCallback;
import awais.instagrabber.dialogs.AccountSwitcherDialogFragment;
import awais.instagrabber.repositories.responses.User;
import awais.instagrabber.utils.Constants;
import awais.instagrabber.utils.CookieUtils;
import awais.instagrabber.utils.FlavorTown;
import awais.instagrabber.utils.TextUtils;
import awais.instagrabber.webservices.ServiceCallback;
import awais.instagrabber.webservices.UserService;

import static awais.instagrabber.utils.Utils.settingsHelper;

public class MorePreferencesFragment extends BasePreferencesFragment {
    private static final String TAG = "MorePreferencesFragment";

    private AccountRepository accountRepository;

    public MorePreferencesFragment() {
    }

    @Override
    void setupPreferenceScreen(final PreferenceScreen screen) {
        final String cookie = settingsHelper.getString(Constants.COOKIE);
        final boolean isLoggedIn = !TextUtils.isEmpty(cookie) && CookieUtils.getUserIdFromCookie(cookie) > 0;
        // screen.addPreference(new MoreHeaderPreference(getContext()));
        final Context context = getContext();
        if (context == null) return;
        accountRepository = AccountRepository.getInstance(AccountDataSource.getInstance(context));
        final PreferenceCategory accountCategory = new PreferenceCategory(context);
        accountCategory.setTitle(R.string.account);
        accountCategory.setIconSpaceReserved(false);
        screen.addPreference(accountCategory);
        if (isLoggedIn) {
            accountCategory.setSummary(R.string.account_hint);
            accountCategory.addPreference(getAccountSwitcherPreference(cookie, context));
            accountCategory.addPreference(getPreference(R.string.logout, R.string.logout_summary, R.drawable.ic_logout_24, preference -> {
                if (getContext() == null) return false;
                CookieUtils.setupCookies("LOGOUT");
                shouldRecreate();
                Toast.makeText(context, R.string.logout_success, Toast.LENGTH_SHORT).show();
                settingsHelper.putString(Constants.COOKIE, "");
                return true;
            }));
        }
        accountRepository.getAllAccounts(new RepositoryCallback<List<Account>>() {
            @Override
            public void onSuccess(@NonNull final List<Account> accounts) {
                if (!isLoggedIn) {
                    if (accounts.size() > 0) {
                        accountCategory.addPreference(getAccountSwitcherPreference(null, context));
                    }
                    // Need to show something to trigger login activity
                    accountCategory.addPreference(getPreference(R.string.add_account, R.drawable.ic_add, preference -> {
                        startActivityForResult(new Intent(getContext(), Login.class), Constants.LOGIN_RESULT_CODE);
                        return true;
                    }));
                }
                if (accounts.size() > 0) {
                    accountCategory
                            .addPreference(getPreference(R.string.remove_all_acc, null, R.drawable.ic_account_multiple_remove_24, preference -> {
                                if (getContext() == null) return false;
                                new AlertDialog.Builder(getContext())
                                        .setTitle(R.string.logout)
                                        .setMessage(R.string.remove_all_acc_warning)
                                        .setPositiveButton(R.string.yes, (dialog, which) -> {
                                            CookieUtils.removeAllAccounts(context, new RepositoryCallback<Void>() {
                                                @Override
                                                public void onSuccess(final Void result) {
                                                    shouldRecreate();
                                                    Toast.makeText(context, R.string.logout_success, Toast.LENGTH_SHORT).show();
                                                    settingsHelper.putString(Constants.COOKIE, "");
                                                }

                                                @Override
                                                public void onDataNotAvailable() {}
                                            });
                                        })
                                        .setNegativeButton(R.string.cancel, null)
                                        .show();
                                return true;
                            }));
                }
            }

            @Override
            public void onDataNotAvailable() {
                Log.d(TAG, "onDataNotAvailable");
                if (!isLoggedIn) {
                    // Need to show something to trigger login activity
                    accountCategory.addPreference(getPreference(R.string.add_account, R.drawable.ic_add, preference -> {
                        startActivityForResult(new Intent(getContext(), Login.class), Constants.LOGIN_RESULT_CODE);
                        return true;
                    }));
                }
            }
        });

        // final PreferenceCategory generalCategory = new PreferenceCategory(context);
        // generalCategory.setTitle(R.string.pref_category_general);
        // generalCategory.setIconSpaceReserved(false);
        // screen.addPreference(generalCategory);
        screen.addPreference(getDivider(context));
        if (isLoggedIn) {
            screen.addPreference(getPreference(R.string.action_notif, R.drawable.ic_not_liked, preference -> {
                final NavDirections navDirections = MorePreferencesFragmentDirections.actionGlobalNotificationsViewerFragment("notif", 0l);
                NavHostFragment.findNavController(this).navigate(navDirections);
                return true;
            }));
            screen.addPreference(getPreference(R.string.action_ayml, R.drawable.ic_suggested_users, preference -> {
                final NavDirections navDirections = MorePreferencesFragmentDirections.actionGlobalNotificationsViewerFragment("ayml", 0l);
                NavHostFragment.findNavController(this).navigate(navDirections);
                return true;
            }));
            screen.addPreference(getPreference(R.string.action_archive, R.drawable.ic_archive, preference -> {
                final NavDirections navDirections = MorePreferencesFragmentDirections.actionGlobalStoryListViewerFragment("archive");
                NavHostFragment.findNavController(this).navigate(navDirections);
                return true;
            }));
        }
        screen.addPreference(getPreference(R.string.title_favorites, R.drawable.ic_star_24, preference -> {
            final NavDirections navDirections = MorePreferencesFragmentDirections.actionMorePreferencesFragmentToFavoritesFragment();
            NavHostFragment.findNavController(this).navigate(navDirections);
            return true;
        }));

        screen.addPreference(getDivider(context));
        screen.addPreference(getPreference(R.string.action_settings, R.drawable.ic_outline_settings_24, preference -> {
            final NavDirections navDirections = MorePreferencesFragmentDirections.actionMorePreferencesFragmentToSettingsPreferencesFragment();
            NavHostFragment.findNavController(this).navigate(navDirections);
            return true;
        }));
        screen.addPreference(getPreference(R.string.backup_and_restore, R.drawable.ic_settings_backup_restore_24, preference -> {
            final NavDirections navDirections = MorePreferencesFragmentDirections.actionMorePreferencesFragmentToBackupPreferencesFragment();
            NavHostFragment.findNavController(this).navigate(navDirections);
            return true;
        }));
        screen.addPreference(getPreference(R.string.action_about, R.drawable.ic_outline_info_24, preference1 -> {
            final NavDirections navDirections = MorePreferencesFragmentDirections.actionMorePreferencesFragmentToAboutFragment();
            NavHostFragment.findNavController(this).navigate(navDirections);
            return true;
        }));

        screen.addPreference(getDivider(context));
        screen.addPreference(getPreference(R.string.version,
                                           BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")",
                                           -1,
                                           preference -> {
                                               FlavorTown.updateCheck((AppCompatActivity) requireActivity(), true);
                                               return true;
                                           }));
        screen.addPreference(getDivider(context));

        final Preference reminderPreference = getPreference(R.string.reminder, R.string.reminder_summary, R.drawable.ic_warning, null);
        reminderPreference.setSelectable(false);
        screen.addPreference(reminderPreference);
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, @Nullable final Intent data) {
        if (resultCode == Constants.LOGIN_RESULT_CODE) {
            if (data == null) return;
            final String cookie = data.getStringExtra("cookie");
            CookieUtils.setupCookies(cookie);
            settingsHelper.putString(Constants.COOKIE, cookie);
            // No use as the timing of show is unreliable
            // Toast.makeText(getContext(), R.string.login_success_loading_cookies, Toast.LENGTH_SHORT).show();

            // adds cookies to database for quick access
            final long uid = CookieUtils.getUserIdFromCookie(cookie);
            final UserService userService = UserService.getInstance();
            userService.getUserInfo(uid, new ServiceCallback<User>() {
                @Override
                public void onSuccess(final User result) {
                    // Log.d(TAG, "adding userInfo: " + result);
                    if (result != null) {
                        accountRepository.insertOrUpdateAccount(
                                uid,
                                result.getUsername(),
                                cookie,
                                result.getFullName(),
                                result.getProfilePicUrl(),
                                new RepositoryCallback<Account>() {
                                    @Override
                                    public void onSuccess(final Account result) {
                                        final FragmentActivity activity = getActivity();
                                        if (activity == null) return;
                                        activity.recreate();
                                    }

                                    @Override
                                    public void onDataNotAvailable() {
                                        Log.e(TAG, "onDataNotAvailable: insert failed");
                                    }
                                });
                    }
                }

                @Override
                public void onFailure(final Throwable t) {
                    Log.e(TAG, "Error fetching user info", t);
                }
            });
        }
    }

    private AccountSwitcherPreference getAccountSwitcherPreference(final String cookie, final Context context) {
        if (context == null) return null;
        return new AccountSwitcherPreference(context, cookie, accountRepository, v -> showAccountSwitcherDialog());
    }

    private void showAccountSwitcherDialog() {
        final AccountSwitcherDialogFragment dialogFragment = new AccountSwitcherDialogFragment(dialog -> {
            dialog.dismiss();
            startActivityForResult(new Intent(getContext(), Login.class), Constants.LOGIN_RESULT_CODE);
        });
        final FragmentManager fragmentManager = getChildFragmentManager();
        dialogFragment.show(fragmentManager, "accountSwitcher");
    }

    private Preference getPreference(final int title,
                                     final int icon,
                                     final Preference.OnPreferenceClickListener clickListener) {
        return getPreference(title, -1, icon, clickListener);
    }

    private Preference getPreference(final int title,
                                     final int summary,
                                     final int icon,
                                     final Preference.OnPreferenceClickListener clickListener) {
        String string = null;
        if (summary > 0) {
            try {
                string = getString(summary);
            } catch (Resources.NotFoundException e) {
                Log.e(TAG, "Error", e);
            }
        }
        return getPreference(title, string, icon, clickListener);
    }

    private Preference getPreference(final int title,
                                     final String summary,
                                     final int icon,
                                     final Preference.OnPreferenceClickListener clickListener) {
        final Context context = getContext();
        if (context == null) return null;
        final Preference preference = new Preference(context);
        if (icon <= 0) preference.setIconSpaceReserved(false);
        if (icon > 0) preference.setIcon(icon);
        preference.setTitle(title);
        if (!TextUtils.isEmpty(summary)) {
            preference.setSummary(summary);
        }
        preference.setOnPreferenceClickListener(clickListener);
        return preference;
    }


    public static class MoreHeaderPreference extends Preference {

        public MoreHeaderPreference(final Context context) {
            super(context);
            setLayoutResource(R.layout.pref_more_header);
            setSelectable(false);
        }
    }

    public static class AccountSwitcherPreference extends Preference {

        private final String cookie;
        private final AccountRepository accountRepository;
        private final View.OnClickListener onClickListener;

        public AccountSwitcherPreference(final Context context,
                                         final String cookie,
                                         final AccountRepository accountRepository,
                                         final View.OnClickListener onClickListener) {
            super(context);
            this.cookie = cookie;
            this.accountRepository = accountRepository;
            this.onClickListener = onClickListener;
            setLayoutResource(R.layout.pref_account_switcher);
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(final PreferenceViewHolder holder) {
            final View root = holder.itemView;
            if (onClickListener != null) root.setOnClickListener(onClickListener);
            final PrefAccountSwitcherBinding binding = PrefAccountSwitcherBinding.bind(root);
            final long uid = CookieUtils.getUserIdFromCookie(cookie);
            if (uid <= 0) return;
            accountRepository.getAccount(uid, new RepositoryCallback<Account>() {
                @Override
                public void onSuccess(final Account account) {
                    binding.getRoot().post(() -> {
                        binding.fullName.setText(account.getFullName());
                        binding.username.setText("@" + account.getUsername());
                        binding.profilePic.setImageURI(account.getProfilePic());
                        binding.getRoot().requestLayout();
                    });
                }

                @Override
                public void onDataNotAvailable() {}
            });
        }
    }
}
