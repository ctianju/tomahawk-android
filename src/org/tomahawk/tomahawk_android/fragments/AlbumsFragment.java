/* == This file is part of Tomahawk Player - <http://tomahawk-player.org> ===
 *
 *   Copyright 2012, Enno Gottschalk <mrmaffen@googlemail.com>
 *
 *   Tomahawk is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Tomahawk is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Tomahawk. If not, see <http://www.gnu.org/licenses/>.
 */
package org.tomahawk.tomahawk_android.fragments;

import org.jdeferred.DoneCallback;
import org.tomahawk.libtomahawk.collection.Album;
import org.tomahawk.libtomahawk.collection.Collection;
import org.tomahawk.libtomahawk.collection.CollectionManager;
import org.tomahawk.libtomahawk.collection.CollectionUtils;
import org.tomahawk.libtomahawk.collection.Playlist;
import org.tomahawk.libtomahawk.collection.TomahawkListItemComparator;
import org.tomahawk.libtomahawk.collection.UserCollection;
import org.tomahawk.libtomahawk.database.DatabaseHelper;
import org.tomahawk.libtomahawk.resolver.Query;
import org.tomahawk.tomahawk_android.R;
import org.tomahawk.tomahawk_android.TomahawkApp;
import org.tomahawk.tomahawk_android.activities.TomahawkMainActivity;
import org.tomahawk.tomahawk_android.adapters.Segment;
import org.tomahawk.tomahawk_android.adapters.TomahawkListAdapter;
import org.tomahawk.tomahawk_android.services.PlaybackService;
import org.tomahawk.tomahawk_android.utils.FragmentUtils;

import android.os.Bundle;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link TomahawkFragment} which shows a set of {@link Album}s inside its {@link
 * se.emilsjolander.stickylistheaders.StickyListHeadersListView}
 */
public class AlbumsFragment extends TomahawkFragment {

    public static final String COLLECTION_ALBUMS_SPINNER_POSITION
            = "org.tomahawk.tomahawk_android.collection_albums_spinner_position";

    @Override
    public void onResume() {
        super.onResume();

        if (getArguments() != null) {
            if (getArguments().containsKey(SHOW_MODE)) {
                mShowMode = getArguments().getInt(SHOW_MODE);
            }
        }
        if (mContainerFragmentClass == null) {
            getActivity().setTitle("");
        }
        updateAdapter();
    }

    /**
     * Called every time an item inside a ListView or GridView is clicked
     *
     * @param view the clicked view
     * @param item the Object which corresponds to the click
     */
    @Override
    public void onItemClick(View view, final Object item) {
        TomahawkMainActivity activity = (TomahawkMainActivity) getActivity();
        if (item instanceof Query) {
            Query query = ((Query) item);
            if (query.isPlayable()) {
                PlaybackService playbackService = activity.getPlaybackService();
                if (playbackService != null
                        && playbackService.getCurrentQuery() == query) {
                    playbackService.playPause();
                } else {
                    Playlist playlist = Playlist.fromQueryList(
                            TomahawkMainActivity.getLifetimeUniqueStringId(), mShownQueries);
                    if (playbackService != null) {
                        playbackService.setPlaylist(playlist, playlist.getEntryWithQuery(query));
                        Class clss = mContainerFragmentClass != null ? mContainerFragmentClass
                                : ((Object) this).getClass();
                        playbackService.setReturnFragment(clss, getArguments());
                        playbackService.start();
                    }
                }
            }
        } else if (item instanceof Album) {
            if (mCollection != null) {
                mCollection.hasAlbumTracks((Album) item).done(new DoneCallback<Boolean>() {
                    @Override
                    public void onDone(Boolean result) {
                        Bundle bundle = new Bundle();
                        bundle.putString(TomahawkFragment.ALBUM, ((Album) item).getCacheKey());
                        if (result) {
                            bundle.putString(TomahawkFragment.COLLECTION_ID, mCollection.getId());
                        } else {
                            bundle.putString(TomahawkFragment.COLLECTION_ID,
                                    TomahawkApp.PLUGINNAME_HATCHET);
                        }
                        bundle.putInt(CONTENT_HEADER_MODE,
                                ContentHeaderFragment.MODE_HEADER_DYNAMIC);
                        FragmentUtils.replace((TomahawkMainActivity) getActivity(),
                                TracksFragment.class, bundle);
                    }
                });
            }
        }
    }

    /**
     * Update this {@link TomahawkFragment}'s {@link TomahawkListAdapter} content
     */
    @Override
    protected void updateAdapter() {
        if (!mIsResumed) {
            return;
        }

        if (mArtist != null) {
            if (mCollection != null
                    && !TomahawkApp.PLUGINNAME_HATCHET.equals(mCollection.getId())) {
                mCollection.getArtistAlbums(mArtist, true).done(new DoneCallback<Set<Album>>() {
                    @Override
                    public void onDone(Set<Album> result) {
                        fillAdapter(new Segment(
                                mCollection.getName() + " " + getString(R.string.albums),
                                new ArrayList<Object>(result), R.integer.grid_column_count,
                                R.dimen.padding_superlarge, R.dimen.padding_superlarge),
                                mCollection);
                    }
                });
            } else {
                CollectionUtils.getArtistAlbums(mArtist, null).done(new DoneCallback<Set<Album>>() {
                    @Override
                    public void onDone(Set<Album> result) {
                        List<Segment> segments = new ArrayList<>();
                        Segment segment = new Segment(R.string.top_albums,
                                new ArrayList<Object>(result),
                                R.integer.grid_column_count, R.dimen.padding_superlarge,
                                R.dimen.padding_superlarge);
                        segments.add(segment);
                        ArrayList<Query> topHits =
                                CollectionUtils.getArtistTopHits(mArtist);
                        segment = new Segment(R.string.top_hits,
                                new ArrayList<Object>(topHits));
                        segment.setShowNumeration(true, 1);
                        segment.setHideArtistName(true);
                        segment.setShowDuration(true);
                        segments.add(segment);
                        mShownQueries = topHits;
                        fillAdapter(segments);
                    }
                });
            }
        } else if (mAlbumArray != null) {
            fillAdapter(new Segment(new ArrayList<Object>(mAlbumArray)));
        } else if (mUser != null) {
            Set<Album> albums = mUser.getStarredAlbums();
            fillAdapter(new Segment(getDropdownPos(COLLECTION_ALBUMS_SPINNER_POSITION),
                    constructDropdownItems(),
                    constructDropdownListener(COLLECTION_ALBUMS_SPINNER_POSITION),
                    new ArrayList<Object>(sortAlbums(new ArrayList<>(albums))),
                    R.integer.grid_column_count, R.dimen.padding_superlarge,
                    R.dimen.padding_superlarge));
        } else {
            final Set<Album> albums = new HashSet<>();
            final Collection collection;
            if (mCollection != null) {
                collection = mCollection;
            } else {
                collection = CollectionManager.getInstance().getCollection(
                        TomahawkApp.PLUGINNAME_USERCOLLECTION);
                albums.addAll(DatabaseHelper.getInstance().getStarredAlbums());
            }
            collection.getAlbums().done(new DoneCallback<Set<Album>>() {
                @Override
                public void onDone(Set<Album> result) {
                    albums.addAll(sortAlbums(new ArrayList<>(result)));
                    fillAdapter(new Segment(getDropdownPos(COLLECTION_ALBUMS_SPINNER_POSITION),
                            constructDropdownItems(),
                            constructDropdownListener(COLLECTION_ALBUMS_SPINNER_POSITION),
                            new ArrayList<Object>(albums), R.integer.grid_column_count,
                            R.dimen.padding_superlarge, R.dimen.padding_superlarge), collection);
                }
            });
        }
    }

    private List<Integer> constructDropdownItems() {
        List<Integer> dropDownItems = new ArrayList<>();
        dropDownItems.add(R.string.collection_dropdown_recently_added);
        dropDownItems.add(R.string.collection_dropdown_alpha);
        dropDownItems.add(R.string.collection_dropdown_alpha_artists);
        return dropDownItems;
    }

    private List<Album> sortAlbums(List<Album> albums) {
        switch (getDropdownPos(COLLECTION_ALBUMS_SPINNER_POSITION)) {
            case 0:
                UserCollection userColl = (UserCollection) CollectionManager.getInstance()
                        .getCollection(TomahawkApp.PLUGINNAME_USERCOLLECTION);
                Collections.sort(albums, new TomahawkListItemComparator(
                        TomahawkListItemComparator.COMPARE_RECENTLY_ADDED,
                        userColl.getAlbumAddedTimeStamps()));
                break;
            case 1:
                Collections.sort(albums, new TomahawkListItemComparator(
                        TomahawkListItemComparator.COMPARE_ALPHA));
                break;
            case 2:
                Collections.sort(albums, new TomahawkListItemComparator(
                        TomahawkListItemComparator.COMPARE_ARTIST_ALPHA));
                break;
        }
        return albums;
    }
}
