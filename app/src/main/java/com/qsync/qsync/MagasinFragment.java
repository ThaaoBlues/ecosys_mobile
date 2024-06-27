/*
 * *
 *  * Created by Théo Mougnibas on 27/06/2024 17:18
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 27/06/2024 17:18
 *
 */

package com.qsync.qsync;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.fragment.app.Fragment;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;

import com.qsync.qsync.databinding.FragmentMagasinBinding;
import com.qsync.qsync.databinding.FragmentSynchronisationsBinding;

public class MagasinFragment extends Fragment {


    private FragmentMagasinBinding binding;
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        binding = FragmentMagasinBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        binding.buttonMagasinVersLargageAerien.setOnClickListener(v ->
                NavHostFragment.findNavController(MagasinFragment.this)
                        .navigate(R.id.action_fragment_magasin_to_LargageAerienFragment)
        );

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }



}