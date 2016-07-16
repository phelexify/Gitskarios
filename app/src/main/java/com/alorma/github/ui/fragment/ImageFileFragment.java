package com.alorma.github.ui.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.AppCompatDrawableManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;
import com.alorma.github.Base64;
import com.alorma.github.R;
import com.alorma.github.sdk.bean.dto.response.Branch;
import com.alorma.github.sdk.bean.dto.response.Content;
import com.alorma.github.sdk.bean.info.FileInfo;
import com.alorma.github.sdk.services.content.GetFileContentClient;
import com.alorma.github.sdk.services.repo.GetRepoBranchesClient;
import com.alorma.github.ui.activity.EditContentActivity;
import com.alorma.github.ui.fragment.base.BaseFragment;
import com.alorma.github.utils.ImageUtils;
import java.util.List;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import uk.co.senab.photoview.PhotoViewAttacher;

public class ImageFileFragment extends BaseFragment {

  public static final String FILE_INFO = "FILE_INFO";
  public static final String FROM_URL = "FROM_URL";
  private static final int RESULT_EDIT = 123;

  PhotoViewAttacher mAttacher;
  private ImageView imageView;
  private View loadingView;

  private FileInfo fileInfo;

  public static ImageFileFragment getInstance(FileInfo info, boolean fromUrl) {
    ImageFileFragment fragment = new ImageFileFragment();
    Bundle args = new Bundle();
    args.putParcelable(FILE_INFO, info);
    args.putBoolean(FROM_URL, fromUrl);
    fragment.setArguments(args);
    return fragment;
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.image_file_fragment, null, false);
  }

  @Override
  protected int getLightTheme() {
    return R.style.AppTheme_Repository;
  }

  @Override
  protected int getDarkTheme() {
    return R.style.AppTheme_Dark_Repository;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(true);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    loadingView = view.findViewById(R.id.loading_view);

    imageView = (ImageView) view.findViewById(R.id.imageView);
    mAttacher = new PhotoViewAttacher(imageView);

    if (getArguments() != null) {

      fileInfo = getArguments().getParcelable(FILE_INFO);
      boolean fromUrl = getArguments().getBoolean(FROM_URL);

      if (fileInfo.content == null) {
        if (fromUrl) {
          getBranches();
        } else {
          getContent();
        }
      } else {
        getActivity().invalidateOptionsMenu();
      }
    }
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    super.onCreateOptionsMenu(menu, inflater);
    if (!ImageUtils.isImage(fileInfo.name)) {
      if (fileInfo.repoInfo.permissions != null && fileInfo.repoInfo.permissions.push) {
        inflater.inflate(R.menu.file_fragment_edit, menu);
      }
    }
  }

  @SuppressLint("NewApi")
  @Override
  public void onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);

    MenuItem menuItem = menu.findItem(R.id.edit);

    if (menuItem != null) {
      Drawable drawable = AppCompatDrawableManager.get().getDrawable(getActivity(), R.drawable.pencil);
      drawable = DrawableCompat.wrap(drawable);
      drawable.setTint(Color.WHITE);
      menuItem.setIcon(drawable);
      menuItem.setEnabled(fileInfo.content != null);
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.edit) {
      editContent();
    }
    return super.onOptionsItemSelected(item);
  }

  private void editContent() {
    Intent launcherIntent = EditContentActivity.createLauncherIntent(getActivity(), fileInfo);
    startActivityForResult(launcherIntent, RESULT_EDIT);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (resultCode == Activity.RESULT_OK) {
      if (requestCode == RESULT_EDIT) {
        getContent();
      }
    }
  }

  private void getBranches() {
    if (fileInfo.repoInfo != null) {
      showProgressDialog();

      GetRepoBranchesClient branchesClient = new GetRepoBranchesClient(fileInfo.repoInfo);
      branchesClient.observable()
          .subscribeOn(Schedulers.io())
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(new ParseBranchesCallback(fileInfo.path));
    }
  }

  protected void getContent() {
    if (fileInfo.repoInfo != null) {
      showProgressDialog();
      GetFileContentClient fileContentClient = new GetFileContentClient(fileInfo);
      fileContentClient.observable()
          .subscribeOn(Schedulers.io())
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(new Subscriber<Content>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(Content content) {
              onContentLoaded(content);
            }
          });
    }
  }

  public void onContentLoaded(Content content) {

    hideProgressDialog();

    if (ImageUtils.isImage(content.name)) {
      if (getActivity() != null && isAdded()) {
        try {
          byte[] imageAsBytes = Base64.decode(content.content);
          Bitmap bitmap = BitmapFactory.decodeByteArray(imageAsBytes, 0, imageAsBytes.length);
          imageView.setVisibility(View.VISIBLE);
          imageView.setImageBitmap(bitmap);
          mAttacher.update();
        } catch (Exception e) {
          Toast.makeText(getActivity(), R.string.error_loading_image, Toast.LENGTH_SHORT).show();
          e.printStackTrace();
        }
      }
    }
  }

  protected void showProgressDialog() {
    loadingView.setVisibility(View.VISIBLE);
  }

  protected void hideProgressDialog() {
    loadingView.setVisibility(View.GONE);
  }

  private class ParseBranchesCallback extends Subscriber<List<Branch>> {
    private String path;

    public ParseBranchesCallback(String path) {
      this.path = path;
    }

    @Override
    public void onCompleted() {

    }

    @Override
    public void onError(Throwable e) {

    }

    @Override
    public void onNext(List<Branch> branches) {
      for (Branch branch : branches) {
        if (path != null && path.contains(branch.name)) {
          fileInfo.repoInfo.branch = branch.name;

          fileInfo.path = fileInfo.path.replace(branch.name + "/", "");
          getContent();
          break;
        }
      }
    }
  }
}
