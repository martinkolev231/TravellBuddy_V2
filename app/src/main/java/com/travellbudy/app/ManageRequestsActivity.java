package com.travellbudy.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.travellbudy.app.databinding.ActivityManageRequestsBinding;
import com.travellbudy.app.databinding.ItemSeatRequestBinding;
import com.travellbudy.app.models.SeatRequest;
import com.travellbudy.app.models.TripMember;

import java.util.ArrayList;
import java.util.List;

public class ManageRequestsActivity extends AppCompatActivity {

    public static final String EXTRA_TRIP_ID = "trip_id";

    private ActivityManageRequestsBinding binding;
    private DatabaseReference requestsRef;
    private DatabaseReference tripRef;
    private RequestAdapter adapter;
    private final List<SeatRequest> requests = new ArrayList<>();
    private ValueEventListener listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityManageRequestsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        String tripId = getIntent().getStringExtra(EXTRA_TRIP_ID);
        if (tripId == null) {
            finish();
            return;
        }

        tripRef = FirebaseDatabase.getInstance().getReference("trips").child(tripId);
        requestsRef = FirebaseDatabase.getInstance().getReference("tripRequests").child(tripId);

        adapter = new RequestAdapter();
        binding.rvRequests.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRequests.setAdapter(adapter);

        loadRequests();
    }

    private void loadRequests() {
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                requests.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    SeatRequest req = child.getValue(SeatRequest.class);
                    if (req != null) {
                        requests.add(req);
                    }
                }
                adapter.notifyDataSetChanged();
                binding.emptyState.setVisibility(requests.isEmpty() ? View.VISIBLE : View.GONE);
                binding.rvRequests.setVisibility(requests.isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ManageRequestsActivity.this, R.string.error_generic, Toast.LENGTH_SHORT).show();
            }
        };
        requestsRef.addValueEventListener(listener);
    }

    private void approveRequest(SeatRequest request) {
        // Use transaction to atomically decrement available seats
        tripRef.child("availableSeats").runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Integer seats = currentData.getValue(Integer.class);
                if (seats == null || seats <= 0) {
                    return Transaction.abort();
                }
                // Decrement by the number of seats requested
                int seatsToDeduct = request.seatsRequested > 0 ? request.seatsRequested : 1;
                currentData.setValue(seats - seatsToDeduct);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (committed) {
                    // Update request status
                    requestsRef.child(request.requestId).child("status").setValue("approved");
                    requestsRef.child(request.requestId).child("updatedAt").setValue(System.currentTimeMillis());

                    // Add rider to tripMembers (required for rating + member-gated access)
                    String tripId = getIntent().getStringExtra(EXTRA_TRIP_ID);
                    if (tripId != null) {
                        TripMember member = new TripMember(
                                request.riderUid, "rider", request.seatsRequested);
                        FirebaseDatabase.getInstance().getReference("tripMembers")
                                .child(tripId).child(request.riderUid)
                                .setValue(member.toMap());
                    }

                    // If seats now 0, update trip status
                    Integer remainingSeats = snapshot.getValue(Integer.class);
                    if (remainingSeats != null && remainingSeats <= 0) {
                        tripRef.child("status").setValue("full");
                    }

                    Toast.makeText(ManageRequestsActivity.this, R.string.success_request_approved, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ManageRequestsActivity.this, R.string.error_trip_full, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void denyRequest(SeatRequest request) {
        requestsRef.child(request.requestId).child("status").setValue("denied");
        requestsRef.child(request.requestId).child("updatedAt").setValue(System.currentTimeMillis());
        Toast.makeText(this, R.string.success_request_denied, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) requestsRef.removeEventListener(listener);
    }

    private class RequestAdapter extends RecyclerView.Adapter<RequestAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemSeatRequestBinding itemBinding = ItemSeatRequestBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new VH(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            SeatRequest req = requests.get(position);
            holder.bind(req);
        }

        @Override
        public int getItemCount() {
            return requests.size();
        }

        class VH extends RecyclerView.ViewHolder {
            private final ItemSeatRequestBinding itemBinding;

            VH(ItemSeatRequestBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;
            }

            void bind(SeatRequest req) {
                itemBinding.tvRiderName.setText(req.riderName);
                itemBinding.tvMessage.setText(req.message != null && !req.message.isEmpty() ? req.message : "");
                itemBinding.tvMessage.setVisibility(req.message != null && !req.message.isEmpty() ? View.VISIBLE : View.GONE);

                boolean isPending = "pending".equals(req.status);
                itemBinding.layoutActions.setVisibility(isPending ? View.VISIBLE : View.GONE);

                // Status label
                switch (req.status != null ? req.status : "") {
                    case "pending":
                        itemBinding.tvStatus.setText(R.string.label_status_pending);
                        itemBinding.tvStatus.setTextColor(getColor(R.color.status_pending));
                        break;
                    case "approved":
                        itemBinding.tvStatus.setText(R.string.label_status_approved);
                        itemBinding.tvStatus.setTextColor(getColor(R.color.status_approved));
                        break;
                    case "denied":
                        itemBinding.tvStatus.setText(R.string.label_status_denied);
                        itemBinding.tvStatus.setTextColor(getColor(R.color.status_denied));
                        break;
                    default:
                        itemBinding.tvStatus.setText(req.status);
                        itemBinding.tvStatus.setTextColor(getColor(R.color.status_cancelled));
                        break;
                }

                itemBinding.btnApprove.setOnClickListener(v -> approveRequest(req));
                itemBinding.btnDeny.setOnClickListener(v -> denyRequest(req));
            }
        }
    }
}

